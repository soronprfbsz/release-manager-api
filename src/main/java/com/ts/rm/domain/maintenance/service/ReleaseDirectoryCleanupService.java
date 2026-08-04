package com.ts.rm.domain.maintenance.service;

import com.ts.rm.domain.maintenance.dto.MaintenanceResultDto;
import com.ts.rm.domain.releaseversion.entity.ReleaseVersion;
import com.ts.rm.domain.releaseversion.repository.ReleaseVersionRepository;
import com.ts.rm.domain.releaseversion.service.ReleaseVersionFileSystemService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Release Directory Cleanup Service
 *
 * <p>NAS(SMB) 환경에서는 다른 클라이언트의 열린 핸들 때문에 버전/빌드 삭제가 best-effort 로
 * 동작하고, 잔존 디렉토리(orphan)가 남는다 (#SMB핸들). 이 reaper 가 versions/ 트리를
 * DB(release_version)와 대조해 orphan 을 주기적으로 정리한다 — 스케줄 실행 시점에는
 * 핸들이 닫혀 있어 삭제가 성공한다.
 *
 * <p>안전 장치:
 * <ul>
 *   <li>DB 에 살아있는 버전/빌드/핫픽스의 경로는 절대 삭제하지 않음 (경로 대조)</li>
 *   <li>경로 해석이 어긋나도 디렉토리 이름이 살아있는 버전/빌드와 같으면 보호 (이름 가드)</li>
 *   <li>quiet 기간(기본 24h) 내에 변경된 디렉토리는 건너뜀 (진행 중 업로드 보호)</li>
 *   <li>알려진 레이아웃 깊이의 디렉토리만 대상 (그 외 항목은 건드리지 않음)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReleaseDirectoryCleanupService {

    private static final String TASK_TYPE = "orphan-directory-cleanup";

    private final ReleaseVersionRepository releaseVersionRepository;
    private final ReleaseVersionFileSystemService fileSystemService;

    @Value("${app.release.base-path:data/release-manager}")
    private String baseReleasePath;

    /**
     * orphan 디렉토리 정리
     *
     * @param quietHours 이 시간 이상 변경이 없는 디렉토리만 삭제 (진행 중 업로드 보호)
     * @return 정리 결과 (deletedCount = 실제 제거된 orphan 디렉토리 수)
     */
    @Transactional(readOnly = true)
    public MaintenanceResultDto.CleanupResult cleanupOrphanDirectories(int quietHours) {
        log.info("orphan 디렉토리 정리 시작 - quietHours: {}", quietHours);

        Path versionsRoot = Paths.get(baseReleasePath, "versions");
        if (!Files.isDirectory(versionsRoot)) {
            return MaintenanceResultDto.CleanupResult.of(TASK_TYPE, 0, "versions 디렉토리가 없습니다.");
        }

        LiveIndex live = buildLiveIndex();
        Instant cutoff = Instant.now().minus(Duration.ofHours(quietHours));

        int deleted = 0;
        for (Path projectDir : listDirectories(versionsRoot)) {
            // standard/{majorMinor}/{version}
            for (Path mmDir : listDirectories(projectDir.resolve("standard"))) {
                deleted += cleanVersionDirectories(mmDir, live, cutoff);
                deleteIfEmpty(mmDir);
            }
            // custom/{siteCode}/{majorMinor}/{version}
            for (Path siteDir : listDirectories(projectDir.resolve("custom"))) {
                for (Path mmDir : listDirectories(siteDir)) {
                    deleted += cleanVersionDirectories(mmDir, live, cutoff);
                    deleteIfEmpty(mmDir);
                }
                deleteIfEmpty(siteDir);
            }
        }

        String message = String.format("orphan 디렉토리 %d개 정리 완료 (quiet %d시간)", deleted, quietHours);
        log.info("orphan 디렉토리 정리 완료 - deletedCount: {}", deleted);
        return MaintenanceResultDto.CleanupResult.of(TASK_TYPE, deleted, message);
    }

    /**
     * DB 의 모든 release_version 행에서 보호 대상 경로/이름 인덱스를 구성한다.
     */
    private LiveIndex buildLiveIndex() {
        Set<Path> paths = new HashSet<>();
        Set<String> versionNames = new HashSet<>();
        Set<String> buildDirNames = new HashSet<>();
        boolean hotfixUnresolved = false;

        List<ReleaseVersion> rows = releaseVersionRepository.findAll();
        for (ReleaseVersion row : rows) {
            try {
                if (row.isBuild()) {
                    // 이름 가드는 경로 해석 실패와 무관하게 항상 등록
                    buildDirNames.add(row.getBuildVersion() + "-"
                            + (row.getBuildIteration() != null ? row.getBuildIteration() : 0));
                    paths.add(normalize(fileSystemService.resolveBuildBasePath(row)));
                } else if (row.isHotfix()) {
                    paths.add(normalize(fileSystemService.resolveHotfixDirectory(row)));
                } else {
                    versionNames.add(row.getVersion());
                    fileSystemService.resolveVersionDirectoryCandidates(row)
                            .forEach(p -> paths.add(normalize(p)));
                }
            } catch (Exception e) {
                // 경로 해석 불가 행(FK 끊김 등)은 이름 가드에 의존하고, 핫픽스는 이번 회차
                // 핫픽스 정리를 통째로 건너뛴다 (지울 수 없는 것보다 안 지우는 게 안전)
                log.warn("live 경로 해석 실패 - releaseVersionId: {}, 사유: {}",
                        row.getReleaseVersionId(), e.getMessage());
                if (row.isHotfix()) {
                    hotfixUnresolved = true;
                }
            }
        }
        return new LiveIndex(paths, versionNames, buildDirNames, hotfixUnresolved);
    }

    /**
     * majorMinor 디렉토리 바로 아래의 버전 디렉토리들을 검사한다.
     * 살아있는 버전이면 내부의 builds/hotfix orphan 을, orphan 버전이면 디렉토리 전체를 정리.
     */
    private int cleanVersionDirectories(Path mmDir, LiveIndex live, Instant cutoff) {
        int deleted = 0;
        for (Path versionDir : listDirectories(mmDir)) {
            if (live.paths().contains(normalize(versionDir))) {
                deleted += cleanChildDirectories(versionDir.resolve("builds"), live, cutoff, true);
                if (!live.hotfixUnresolved()) {
                    deleted += cleanChildDirectories(versionDir.resolve("hotfix"), live, cutoff, false);
                }
            } else if (live.versionNames().contains(versionDir.getFileName().toString())) {
                log.warn("orphan 후보이나 살아있는 버전명과 일치 — 보호 (경로 mismatch 의심): {}", versionDir);
            } else if (isQuiet(versionDir, cutoff)) {
                log.info("orphan 버전 디렉토리 삭제: {}", versionDir);
                fileSystemService.deleteDirectory(versionDir);
                if (!Files.exists(versionDir)) {
                    deleted++;
                }
            }
        }
        return deleted;
    }

    /**
     * 버전 디렉토리 안의 builds/ 또는 hotfix/ 컨테이너에서 orphan 하위 디렉토리를 정리한다.
     */
    private int cleanChildDirectories(Path containerDir, LiveIndex live, Instant cutoff,
            boolean useBuildNameGuard) {
        int deleted = 0;
        for (Path child : listDirectories(containerDir)) {
            if (live.paths().contains(normalize(child))) {
                continue;
            }
            if (useBuildNameGuard && live.buildDirNames().contains(child.getFileName().toString())) {
                log.warn("orphan 후보이나 살아있는 빌드명과 일치 — 보호 (경로 mismatch 의심): {}", child);
                continue;
            }
            if (!isQuiet(child, cutoff)) {
                continue;
            }
            log.info("orphan 하위 디렉토리 삭제: {}", child);
            fileSystemService.deleteDirectory(child);
            if (!Files.exists(child)) {
                deleted++;
            }
        }
        deleteIfEmpty(containerDir);
        return deleted;
    }

    private List<Path> listDirectories(Path dir) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> entries = Files.list(dir)) {
            return entries.filter(Files::isDirectory).toList();
        } catch (IOException e) {
            log.warn("디렉토리 목록 조회 실패 (건너뜀): {} - {}", dir, e.toString());
            return List.of();
        }
    }

    private boolean isQuiet(Path dir, Instant cutoff) {
        try {
            return Files.getLastModifiedTime(dir).toInstant().isBefore(cutoff);
        } catch (IOException e) {
            log.warn("mtime 조회 실패 — 보호를 위해 건너뜀: {} - {}", dir, e.toString());
            return false;
        }
    }

    private void deleteIfEmpty(Path dir) {
        try {
            if (Files.isDirectory(dir) && fileSystemService.isDirectoryEmpty(dir)) {
                Files.delete(dir);
                log.info("빈 디렉토리 정리: {}", dir);
            }
        } catch (IOException e) {
            log.debug("빈 디렉토리 정리 실패 (무시): {} - {}", dir, e.toString());
        }
    }

    private Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }

    /**
     * DB 기준 보호 대상 인덱스.
     *
     * @param paths            살아있는 버전/빌드/핫픽스의 정규화 경로
     * @param versionNames     살아있는 버전 문자열 (경로 mismatch 대비 이름 가드)
     * @param buildDirNames    살아있는 빌드 디렉토리명 "{buildVersion}-{iteration}"
     * @param hotfixUnresolved 경로 해석 불가 핫픽스 행 존재 → 핫픽스 정리 전체 스킵
     */
    private record LiveIndex(Set<Path> paths, Set<String> versionNames,
                             Set<String> buildDirNames, boolean hotfixUnresolved) {
    }
}
