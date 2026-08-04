package com.ts.rm.domain.releaseversion.service;

import com.ts.rm.domain.site.entity.Site;
import com.ts.rm.domain.releaseversion.entity.ReleaseVersion;
import com.ts.rm.domain.releaseversion.util.VersionParser.VersionInfo;
import com.ts.rm.global.exception.BusinessException;
import com.ts.rm.global.exception.ErrorCode;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * ReleaseVersion FileSystem Service
 *
 * <p>릴리즈 버전의 파일 시스템 관리 (디렉토리 생성/삭제)
 */
@Slf4j
@Service
public class ReleaseVersionFileSystemService {

    @Value("${app.release.base-path:data/release-manager}")
    private String baseReleasePath;

    /**
     * 릴리즈 디렉토리 구조 생성
     *
     * <pre>
     * versions/{projectId}/{type}/{majorMinor}.x/{version}/mariadb/
     * versions/{projectId}/{type}/{majorMinor}.x/{version}/cratedb/
     * </pre>
     */
    public void createDirectoryStructure(ReleaseVersion version, Site site) {
        try {
            String projectId = version.getProject() != null ? version.getProject().getProjectId() : "infraeye2";
            String basePath;

            if ("STANDARD".equals(version.getReleaseType())) {
                basePath = String.format("versions/%s/standard/%s/%s",
                        projectId,
                        version.getMajorMinor(),
                        version.getVersion());
            } else {
                // CUSTOM인 경우 사이트 코드 사용
                String siteCode = site != null ? site.getSiteCode() : "unknown";
                basePath = String.format("versions/%s/custom/%s/%s/%s",
                        projectId,
                        siteCode,
                        version.getMajorMinor(),
                        version.getVersion());
            }

            // 디렉토리 생성
            Path mariadbPath = Paths.get(baseReleasePath, basePath, "mariadb");
            Path cratedbPath = Paths.get(baseReleasePath, basePath, "cratedb");

            Files.createDirectories(mariadbPath);
            Files.createDirectories(cratedbPath);

            log.info("릴리즈 디렉토리 구조 생성 완료: {}", basePath);

        } catch (IOException e) {
            log.error("디렉토리 생성 실패: {}", version.getVersion(), e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "디렉토리 생성 실패: " + e.getMessage());
        }
    }

    /**
     * 표준 버전 디렉토리 생성
     *
     * @param versionInfo 버전 정보
     * @param projectId   프로젝트 ID
     * @return 생성된 버전 경로
     */
    public Path createVersionDirectory(VersionInfo versionInfo, String projectId) throws IOException {
        // 경로: release-manager/versions/{projectId}/standard/{major}.{minor}.x/{version}/
        String majorMinor = versionInfo.getMajorMinor();
        String version = versionInfo.getMajorVersion() + "." + versionInfo.getMinorVersion() + "." + versionInfo.getPatchVersion();

        Path versionPath = Paths.get(baseReleasePath, "versions", projectId, "standard",
                majorMinor, version);

        Files.createDirectories(versionPath);
        log.info("표준 버전 디렉토리 생성: {}", versionPath);

        return versionPath;
    }

    /**
     * 커스텀 버전 디렉토리 생성
     *
     * @param projectId        프로젝트 ID
     * @param siteCode     사이트 코드
     * @param customMajorMinor 커스텀 메이저.마이너 (예: 1.0.x)
     * @param customVersion    커스텀 버전 (예: 1.0.0)
     * @return 생성된 버전 경로
     */
    public Path createCustomVersionDirectory(String projectId, String siteCode,
                                              String customMajorMinor, String customVersion) throws IOException {
        // 경로: release-manager/versions/{projectId}/custom/{siteCode}/{customMajorMinor}/{customVersion}/
        Path versionPath = Paths.get(baseReleasePath, "versions", projectId, "custom",
                siteCode, customMajorMinor, customVersion);

        Files.createDirectories(versionPath);
        log.info("커스텀 버전 디렉토리 생성: {}", versionPath);

        return versionPath;
    }

    /**
     * 버전 디렉토리 삭제
     *
     * @param version 릴리즈 버전 엔티티
     */
    public void deleteVersionDirectory(ReleaseVersion version) {
        Path versionPath = resolveExistingVersionDirectory(version);

        log.info("버전 디렉토리 삭제 시도: {} (exists: {})", versionPath, Files.exists(versionPath));
        if (Files.exists(versionPath)) {
            // NAS(SMB)에서 다른 클라이언트(Windows 탐색기 등)가 파일 핸들을 열고 있으면
            // 파일 unlink 는 성공 응답 후 지연되고(delete-on-close) 부모 rmdir 이
            // DirectoryNotEmptyException 으로 실패한다. strict 로 두면 트랜잭션 롤백으로
            // 파일만 지워지고 DB 행이 남는 반파 상태가 반복된다 (#SMB핸들). best-effort 로
            // 지우고 DB 삭제는 진행시킨다 — 잔존 디렉토리는 orphan 정리 스케줄이 청소한다.
            deleteDirectory(versionPath);
            log.info("버전 디렉토리 삭제(best-effort) 완료: {}", versionPath);

            // 빈 major.minor 디렉토리도 정리
            try {
                Path parentPath = versionPath.getParent();
                if (parentPath != null && Files.exists(parentPath) && isDirectoryEmpty(parentPath)) {
                    Files.delete(parentPath);
                    log.info("빈 major.minor 디렉토리 삭제: {}", parentPath);
                }
            } catch (IOException e) {
                log.warn("major.minor 디렉토리 삭제 실패: {}", versionPath.getParent(), e);
            }
        } else {
            // 경로를 못 찾으면 조용히 넘어가지 말고 흔적을 남긴다 — 과거 커스텀 경로 mismatch 로
            // DB 만 삭제되고 파일이 NAS 에 orphan 으로 남던 회귀(#커스텀삭제)의 재발 감지용.
            log.warn("삭제할 버전 디렉토리를 찾지 못했습니다 (이미 삭제되었거나 경로 mismatch): {}", versionPath);
        }
    }

    /**
     * 버전 디렉토리 후보 경로 목록을 반환한다 (존재 여부 무관).
     *
     * <p>STANDARD 는 base majorMinor 경로 하나뿐이다. CUSTOM 은 생성 경로에 따라 두 갈래로 나뉜다:
     * ZIP 생성({@link #createCustomVersionDirectory})은 <b>custom</b> majorMinor("1.0.x")를,
     * 레거시 비-ZIP 생성({@link #createDirectoryStructure})은 <b>base</b> majorMinor("1.1.x")를
     * 쓴다. 과거 삭제는 base majorMinor 만 보고 ZIP 레이아웃의 디렉토리를 놓쳐, DB 만 삭제되고
     * NAS 파일이 orphan 으로 남았다(#커스텀삭제). orphan 정리(reaper)도 이 후보 목록을 보호
     * 대상(live)으로 사용한다 — 레이아웃 판단은 반드시 이 메서드 하나로 모은다.
     */
    public List<Path> resolveVersionDirectoryCandidates(ReleaseVersion version) {
        String projectId = version.getProject() != null ? version.getProject().getProjectId() : "infraeye2";
        if ("STANDARD".equals(version.getReleaseType())) {
            return List.of(Paths.get(baseReleasePath, "versions", projectId, "standard",
                    version.getMajorMinor(), version.getVersion()));
        }

        String siteCode = version.getSite() != null
                ? version.getSite().getSiteCode()
                : "unknown";

        List<Path> candidates = new ArrayList<>();
        // 운영 ZIP 생성 레이아웃 (custom majorMinor) — getCustomMajorMinor 는 커스텀 버전 숫자가
        // 모두 있을 때만 값을 주므로 null 가드.
        if (version.getCustomMajorMinor() != null) {
            candidates.add(Paths.get(baseReleasePath, "versions", projectId, "custom",
                    siteCode, version.getCustomMajorMinor(), version.getVersion()));
        }
        // 레거시 비-ZIP 생성 레이아웃 (base majorMinor)
        candidates.add(Paths.get(baseReleasePath, "versions", projectId, "custom",
                siteCode, version.getMajorMinor(), version.getVersion()));
        return candidates;
    }

    /**
     * 삭제 대상 버전 디렉토리 경로를 해석한다 — 후보 중 실제 존재하는 첫 경로,
     * 없으면 대표 경로(첫 후보)를 반환해 no-op + WARN 으로 이어지게 한다.
     */
    private Path resolveExistingVersionDirectory(ReleaseVersion version) {
        List<Path> candidates = resolveVersionDirectoryCandidates(version);
        return candidates.stream()
                .filter(Files::exists)
                .findFirst()
                .orElse(candidates.get(0));
    }

    /**
     * 디렉토리 재귀 삭제 (best-effort).
     *
     * <p>실패해도 호출자가 진행해야 하는 모든 삭제 경로에서 사용한다. NAS(SMB) 환경에서는
     * 다른 클라이언트의 열린 핸들 때문에 부분 실패가 정상 상황이므로, IOException 은 로그만
     * 남기고 swallow 한다. 잔존물은 orphan 정리 스케줄이 나중에 청소한다.
     */
    public void deleteDirectory(Path directory) {
        try {
            walkAndDelete(directory);
        } catch (IOException e) {
            log.error("디렉토리 삭제 실패 (best-effort): {}", directory, e);
        }
    }

    private void walkAndDelete(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        // CIFS 마운트에서 일시적 락/race 로 한 항목 삭제가 실패하면 walkFileTree 가
        // 그 자리에서 중단되어 디렉토리가 부분 상태로 남는다. 운영자가 재시도해도
        // 매번 다른 항목에서 fail 할 수 있다 → best-effort 로 끝까지 순회 후
        // 실패 항목을 모아 한 번에 보고한다.
        List<Path> failedItems = new ArrayList<>();
        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                try {
                    Files.delete(file);
                } catch (IOException e) {
                    log.warn("파일 삭제 실패 (계속 진행): {} - {}", file, e.toString());
                    failedItems.add(file);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                log.warn("파일 방문 실패 (계속 진행): {} - {}", file, exc.toString());
                failedItems.add(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                if (exc != null) {
                    log.warn("하위 순회 중 오류 (계속 진행): {} - {}", dir, exc.toString());
                }
                try {
                    Files.delete(dir);
                } catch (IOException e) {
                    log.warn("디렉토리 삭제 실패 (계속 진행): {} - {}", dir, e.toString());
                    failedItems.add(dir);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        if (!failedItems.isEmpty()) {
            String preview = failedItems.stream()
                    .limit(5)
                    .map(Path::toString)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
            throw new IOException(String.format(
                    "일부 항목 삭제 실패 (%d개). 예: %s%s",
                    failedItems.size(), preview,
                    failedItems.size() > 5 ? " ..." : ""));
        }
        log.info("디렉토리 삭제 완료: {}", directory);
    }

    /**
     * 디렉토리가 비어있는지 확인
     */
    public boolean isDirectoryEmpty(Path directory) throws IOException {
        try (var entries = Files.list(directory)) {
            return entries.findAny().isEmpty();
        }
    }

    /**
     * 파일시스템 롤백 (버전 디렉토리 및 release_metadata.json 복원)
     *
     * @param versionDir 생성된 버전 디렉토리 경로
     * @param projectId  프로젝트 ID
     * @param version    버전 번호
     */
    public void rollbackFileSystem(String versionDir, String projectId, String version) {
        try {
            // 1. 버전 디렉토리 삭제
            Path versionPath = Paths.get(versionDir);
            if (Files.exists(versionPath)) {
                log.warn("Rolling back: Deleting version directory {}", versionDir);
                deleteDirectory(versionPath);
            }

            // 2. 빈 major.minor 디렉토리 정리
            Path parentPath = versionPath.getParent();
            if (parentPath != null && Files.exists(parentPath) && isDirectoryEmpty(parentPath)) {
                log.warn("Rolling back: Deleting empty major.minor directory {}", parentPath);
                Files.delete(parentPath);
            }

        } catch (Exception e) {
            log.error("Failed to rollback filesystem for version {}", version, e);
            // 롤백 실패는 로그만 남기고 예외를 던지지 않음 (원본 예외가 중요)
        }
    }

    /**
     * 핫픽스 디렉토리 구조 생성
     *
     * <pre>
     * versions/{projectId}/{type}/{majorMinor}.x/{version}/hotfix/{hotfixVersion}/mariadb/
     * versions/{projectId}/{type}/{majorMinor}.x/{version}/hotfix/{hotfixVersion}/cratedb/
     * </pre>
     *
     * @param hotfixVersion     핫픽스 버전 엔티티
     * @param hotfixBaseVersion 핫픽스 원본 버전 엔티티
     */
    public void createHotfixDirectoryStructure(ReleaseVersion hotfixVersion, ReleaseVersion hotfixBaseVersion) {
        try {
            String projectId = hotfixBaseVersion.getProject() != null ? hotfixBaseVersion.getProject().getProjectId() : "infraeye2";
            String basePath;

            if ("STANDARD".equals(hotfixBaseVersion.getReleaseType())) {
                basePath = String.format("versions/%s/standard/%s/%s/hotfix/%d",
                        projectId,
                        hotfixBaseVersion.getMajorMinor(),
                        hotfixBaseVersion.getVersion(),
                        hotfixVersion.getHotfixVersion());
            } else {
                // CUSTOM인 경우 사이트 코드 사용
                String siteCode = hotfixBaseVersion.getSite() != null
                        ? hotfixBaseVersion.getSite().getSiteCode()
                        : "unknown";
                basePath = String.format("versions/%s/custom/%s/%s/%s/hotfix/%d",
                        projectId,
                        siteCode,
                        hotfixBaseVersion.getMajorMinor(),
                        hotfixBaseVersion.getVersion(),
                        hotfixVersion.getHotfixVersion());
            }

            // 디렉토리 생성
            Path mariadbPath = Paths.get(baseReleasePath, basePath, "mariadb");
            Path cratedbPath = Paths.get(baseReleasePath, basePath, "cratedb");

            Files.createDirectories(mariadbPath);
            Files.createDirectories(cratedbPath);

            log.info("핫픽스 디렉토리 구조 생성 완료: {}", basePath);

        } catch (IOException e) {
            log.error("핫픽스 디렉토리 생성 실패: {}", hotfixVersion.getFullVersion(), e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "핫픽스 디렉토리 생성 실패: " + e.getMessage());
        }
    }

    /**
     * 핫픽스 버전 디렉토리 삭제
     *
     * @param hotfixVersion 핫픽스 버전 엔티티
     */
    public void deleteHotfixDirectory(ReleaseVersion hotfixVersion) {
        if (hotfixVersion.getHotfixBaseVersion() == null) {
            log.warn("핫픽스의 원본 버전이 없습니다: {}", hotfixVersion.getReleaseVersionId());
            return;
        }

        Path hotfixPath = resolveHotfixDirectory(hotfixVersion);

        log.info("핫픽스 디렉토리 삭제 시도: {} (exists: {})", hotfixPath, Files.exists(hotfixPath));
        if (Files.exists(hotfixPath)) {
            // deleteVersionDirectory 와 동일 — SMB 핸들 지연으로 strict 는 반파 상태를 만든다 (#SMB핸들)
            deleteDirectory(hotfixPath);
            log.info("핫픽스 디렉토리 삭제(best-effort) 완료: {}", hotfixPath);

            // 빈 hotfix 디렉토리도 정리
            try {
                Path parentPath = hotfixPath.getParent();  // hotfix 디렉토리
                if (parentPath != null && Files.exists(parentPath) && isDirectoryEmpty(parentPath)) {
                    Files.delete(parentPath);
                    log.info("빈 hotfix 디렉토리 삭제: {}", parentPath);
                }
            } catch (IOException e) {
                log.warn("hotfix 디렉토리 삭제 실패: {}", hotfixPath.getParent(), e);
            }
        } else {
            // deleteVersionDirectory / deleteBuildDirectory 와 동일하게 silent-skip 을 흔적으로 남긴다.
            log.warn("삭제할 핫픽스 디렉토리를 찾지 못했습니다 (이미 삭제되었거나 경로 mismatch): {}", hotfixPath);
        }
    }

    /**
     * 핫픽스 디렉토리 경로 계산 (생성하지 않음).
     *
     * <pre>
     * STANDARD: versions/{projectId}/standard/{majorMinor}/{version}/hotfix/{hotfixVersion}
     * CUSTOM:   versions/{projectId}/custom/{siteCode}/{majorMinor}/{version}/hotfix/{hotfixVersion}
     * </pre>
     *
     * @param hotfixVersion 핫픽스 버전 엔티티 (hotfixBaseVersion 이 채워져 있어야 함)
     */
    public Path resolveHotfixDirectory(ReleaseVersion hotfixVersion) {
        ReleaseVersion hotfixBaseVersion = hotfixVersion.getHotfixBaseVersion();
        if (hotfixBaseVersion == null) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "핫픽스의 원본 버전이 비어있어 경로를 계산할 수 없습니다 (hotfixVersionId: "
                            + hotfixVersion.getReleaseVersionId() + ")");
        }
        String projectId = hotfixBaseVersion.getProject() != null ? hotfixBaseVersion.getProject().getProjectId() : "infraeye2";

        if ("STANDARD".equals(hotfixBaseVersion.getReleaseType())) {
            return Paths.get(baseReleasePath, "versions", projectId, "standard",
                    hotfixBaseVersion.getMajorMinor(), hotfixBaseVersion.getVersion(),
                    "hotfix", String.valueOf(hotfixVersion.getHotfixVersion()));
        }
        String siteCode = hotfixBaseVersion.getSite() != null
                ? hotfixBaseVersion.getSite().getSiteCode()
                : "unknown";
        return Paths.get(baseReleasePath, "versions", projectId, "custom",
                siteCode, hotfixBaseVersion.getMajorMinor(), hotfixBaseVersion.getVersion(),
                "hotfix", String.valueOf(hotfixVersion.getHotfixVersion()));
    }

    /**
     * 빌드 디렉토리 베이스 경로 계산 (생성하지 않음).
     *
     * <pre>
     * STANDARD: versions/{projectId}/standard/{majorMinor}/{version}/builds/{buildVersion}-{iteration}
     * CUSTOM:   versions/{projectId}/custom/{siteCode}/{majorMinor}/{version}/builds/{buildVersion}-{iteration}
     * </pre>
     *
     * @param buildVersionEntity 빌드 버전 엔티티 (buildBaseVersion / buildVersion / buildIteration 포함)
     * @return 빌드 디렉토리 경로 (예: .../builds/260430-1)
     */
    public Path resolveBuildBasePath(ReleaseVersion buildVersionEntity) {
        ReleaseVersion baseVersion = buildVersionEntity.getBuildBaseVersion();
        if (baseVersion == null) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "빌드의 원본 버전이 비어있어 경로를 계산할 수 없습니다 (buildVersionId: "
                            + buildVersionEntity.getReleaseVersionId() + ")");
        }
        String projectId = baseVersion.getProject() != null ? baseVersion.getProject().getProjectId() : "infraeye2";
        String dirName = buildVersionEntity.getBuildVersion()
                + "-" + (buildVersionEntity.getBuildIteration() != null ? buildVersionEntity.getBuildIteration() : 0);

        if ("STANDARD".equals(baseVersion.getReleaseType())) {
            return Paths.get(baseReleasePath, "versions", projectId, "standard",
                    baseVersion.getMajorMinor(), baseVersion.getVersion(),
                    "builds", dirName);
        }

        String siteCode = baseVersion.getSite() != null
                ? baseVersion.getSite().getSiteCode()
                : "unknown";
        return Paths.get(baseReleasePath, "versions", projectId, "custom",
                siteCode, baseVersion.getMajorMinor(), baseVersion.getVersion(),
                "builds", dirName);
    }

    /**
     * 빌드 카테고리(web/engine) 경로 반환
     *
     * @param baseVersion  빌드 원본 버전
     * @param buildVersion 빌드 버전 번호
     * @param category     "web", "engine" 중 하나
     * @return 카테고리 경로
     */
    public Path resolveBuildCategoryPath(ReleaseVersion buildVersionEntity, String category) {
        if (!"web".equals(category) && !"engine".equals(category)) {
            throw new IllegalArgumentException("빌드 카테고리는 web, engine 중 하나여야 합니다: " + category);
        }
        return resolveBuildBasePath(buildVersionEntity).resolve(category);
    }

    /**
     * 빌드 디렉토리 삭제
     *
     * @param buildVersion 빌드 버전 엔티티 (buildBaseVersion 이 채워져 있어야 함)
     */
    public void deleteBuildDirectory(ReleaseVersion buildVersion) {
        if (buildVersion.getBuildBaseVersion() == null) {
            log.error("빌드의 원본 버전(build_base_version_id)이 없어 디렉토리 경로를 계산할 수 없습니다 - buildVersionId: {}",
                    buildVersion.getReleaseVersionId());
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "빌드의 원본 버전 정보가 없어 빌드 디렉토리를 삭제할 수 없습니다 (buildVersionId: "
                            + buildVersion.getReleaseVersionId() + "). 데이터를 확인해주세요.");
        }

        Path buildPath = resolveBuildBasePath(buildVersion);
        log.info("빌드 디렉토리 삭제 시도: {} (exists: {})", buildPath, Files.exists(buildPath));

        if (Files.exists(buildPath)) {
            // CIFS 마운트(/app/resources)에서는 빌드 잔파일·빈 디렉토리 삭제가 간헐 실패한다.
            // strict 로 두면 부분 실패가 BusinessException → 트랜잭션 롤백을 유발해, 빌드가
            // 끝내 삭제되지 않고 부분 삭제 상태로 남는다 (#CIFS 재발). 빌드 잔파일은 참조 FK 가
            // 없는 무해한 orphan 이므로 best-effort 로 지우고 DB 행 삭제는 계속 진행시킨다.
            deleteDirectory(buildPath);
            log.info("빌드 디렉토리 삭제(best-effort) 완료: {}", buildPath);

            // 빈 builds 디렉토리도 정리
            try {
                Path parentPath = buildPath.getParent();  // builds 디렉토리
                if (parentPath != null && Files.exists(parentPath) && isDirectoryEmpty(parentPath)) {
                    Files.delete(parentPath);
                    log.info("빈 builds 디렉토리 삭제: {}", parentPath);
                }
            } catch (IOException e) {
                log.warn("builds 디렉토리 삭제 실패: {}", buildPath.getParent(), e);
            }
        } else {
            log.warn("빌드 디렉토리가 파일시스템에 존재하지 않습니다 (이미 삭제되었거나 경로 mismatch): {}", buildPath);
        }
    }
}
