package com.ts.rm.domain.releaseversion.service;

import com.ts.rm.domain.releaseversion.dto.ReleaseVersionDto;
import com.ts.rm.domain.releaseversion.entity.ReleaseVersion;
import com.ts.rm.domain.releaseversion.repository.ReleaseVersionRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 패치 범위 안의 빌드 후보를 빌드 디렉토리에서 직접 walk 하여 집계하는 서비스.
 *
 * <p>빌드 산출물의 진실의 원천은 빌드 디렉토리이며 (commit 00ee8f8), 본 서비스는
 * ReleaseFile 인덱스를 거치지 않고 디렉토리 안의 web/, engine/{engineName}/, engine 직속
 * 파일 존재 여부로 후보를 만든다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BuildsInRangeService {

    private static final String UNKNOWN_ENGINE = "UNKNOWN";

    private final ReleaseVersionRepository releaseVersionRepository;
    private final ReleaseVersionFileSystemService fileSystemService;

    public ReleaseVersionDto.BuildsInRangeResponse getBuildsInRange(
            String projectId, Long fromBaseId, Long toBaseId, Long customerId) {

        List<ReleaseVersion> builds = releaseVersionRepository
                .findBuildsInBaseRange(projectId, fromBaseId, toBaseId, customerId);

        List<ReleaseVersionDto.BuildCandidate> webCandidates = new ArrayList<>();
        Map<String, List<ReleaseVersionDto.BuildCandidate>> engineMap = new LinkedHashMap<>();

        for (ReleaseVersion bv : builds) {
            Path buildBasePath = fileSystemService.resolveBuildBasePath(bv);
            if (!Files.isDirectory(buildBasePath)) {
                continue;
            }

            ReleaseVersionDto.BuildCandidate candidate = new ReleaseVersionDto.BuildCandidate(
                    bv.getReleaseVersionId(),
                    bv.getFullVersion(),
                    bv.getCreatedAt(),
                    false  // isLatest 는 정렬 후 첫 항목에만 true
            );

            if (hasFiles(buildBasePath.resolve("web"))) {
                webCandidates.add(candidate);
            }

            for (String engineName : engineNamesInBuild(buildBasePath.resolve("engine"))) {
                engineMap.computeIfAbsent(engineName, k -> new ArrayList<>()).add(candidate);
            }
        }

        List<ReleaseVersionDto.BuildCandidate> webSorted = markLatestFirst(webCandidates);

        List<String> sortedEngineNames = new ArrayList<>(engineMap.keySet());
        sortedEngineNames.sort(Comparator.naturalOrder());
        List<ReleaseVersionDto.EngineGroup> engineGroups = new ArrayList<>();
        for (String name : sortedEngineNames) {
            engineGroups.add(new ReleaseVersionDto.EngineGroup(name, markLatestFirst(engineMap.get(name))));
        }

        List<ReleaseVersionDto.HotfixInRangeInfo> hotfixes = releaseVersionRepository
                .findHotfixesInBaseRange(projectId, fromBaseId, toBaseId, customerId).stream()
                .map(h -> new ReleaseVersionDto.HotfixInRangeInfo(
                        h.getReleaseVersionId(), h.getFullVersion(), h.getHotfixVersion()))
                .toList();

        return new ReleaseVersionDto.BuildsInRangeResponse(webSorted, engineGroups, hotfixes);
    }

    /**
     * 디렉토리 안에 정규 파일이 1개 이상 있는지 검사 (any subdirectory depth).
     */
    private boolean hasFiles(Path dir) {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        try (var stream = Files.walk(dir)) {
            return stream.anyMatch(Files::isRegularFile);
        } catch (IOException e) {
            log.warn("디렉토리 walk 실패 (false 로 처리): {}", dir, e);
            return false;
        }
    }

    /**
     * engine/ 디렉토리 안의 1단계 하위 디렉토리명을 수집한다.
     * 하위 디렉토리는 정규 파일이 1개 이상일 때만 포함하며,
     * engine/ 직속 정규 파일이 있으면 UNKNOWN 그룹도 함께 반환.
     */
    private TreeSet<String> engineNamesInBuild(Path engineDir) {
        TreeSet<String> names = new TreeSet<>();
        if (!Files.isDirectory(engineDir)) {
            return names;
        }
        try (var stream = Files.list(engineDir)) {
            stream.forEach(child -> {
                if (Files.isDirectory(child)) {
                    if (hasFiles(child)) {
                        names.add(child.getFileName().toString());
                    }
                } else if (Files.isRegularFile(child)) {
                    names.add(UNKNOWN_ENGINE);
                }
            });
        } catch (IOException e) {
            log.warn("engine 디렉토리 list 실패: {}", engineDir, e);
        }
        return names;
    }

    /**
     * 입력 후보 리스트의 첫 항목에 isLatest=true 를 부여한 새 리스트를 반환.
     * 입력은 build_version DESC 정렬되어 있다고 가정한다 (Repository 가 보장).
     */
    private List<ReleaseVersionDto.BuildCandidate> markLatestFirst(List<ReleaseVersionDto.BuildCandidate> input) {
        if (input.isEmpty()) {
            return List.of();
        }
        List<ReleaseVersionDto.BuildCandidate> out = new ArrayList<>(input.size());
        for (int i = 0; i < input.size(); i++) {
            ReleaseVersionDto.BuildCandidate c = input.get(i);
            out.add(new ReleaseVersionDto.BuildCandidate(
                    c.buildVersionId(), c.fullVersion(), c.createdAt(), i == 0));
        }
        return out;
    }
}
