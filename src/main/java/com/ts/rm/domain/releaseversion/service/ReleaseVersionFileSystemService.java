package com.ts.rm.domain.releaseversion.service;

import com.ts.rm.domain.customer.entity.Customer;
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
    public void createDirectoryStructure(ReleaseVersion version, Customer customer) {
        try {
            String projectId = version.getProject() != null ? version.getProject().getProjectId() : "infraeye2";
            String basePath;

            if ("STANDARD".equals(version.getReleaseType())) {
                basePath = String.format("versions/%s/standard/%s/%s",
                        projectId,
                        version.getMajorMinor(),
                        version.getVersion());
            } else {
                // CUSTOM인 경우 고객사 코드 사용
                String customerCode = customer != null ? customer.getCustomerCode() : "unknown";
                basePath = String.format("versions/%s/custom/%s/%s/%s",
                        projectId,
                        customerCode,
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
     * @param customerCode     고객사 코드
     * @param customMajorMinor 커스텀 메이저.마이너 (예: 1.0.x)
     * @param customVersion    커스텀 버전 (예: 1.0.0)
     * @return 생성된 버전 경로
     */
    public Path createCustomVersionDirectory(String projectId, String customerCode,
                                              String customMajorMinor, String customVersion) throws IOException {
        // 경로: release-manager/versions/{projectId}/custom/{customerCode}/{customMajorMinor}/{customVersion}/
        Path versionPath = Paths.get(baseReleasePath, "versions", projectId, "custom",
                customerCode, customMajorMinor, customVersion);

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
        String projectId = version.getProject() != null ? version.getProject().getProjectId() : "infraeye2";
        Path versionPath;

        if ("STANDARD".equals(version.getReleaseType())) {
            versionPath = Paths.get(baseReleasePath, "versions", projectId, "standard",
                    version.getMajorMinor(), version.getVersion());
        } else {
            String customerCode = version.getCustomer() != null
                    ? version.getCustomer().getCustomerCode()
                    : "unknown";
            versionPath = Paths.get(baseReleasePath, "versions", projectId, "custom",
                    customerCode, version.getMajorMinor(), version.getVersion());
        }

        if (Files.exists(versionPath)) {
            deleteDirectory(versionPath);
            log.info("버전 디렉토리 삭제 완료: {}", versionPath);

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
        }
    }

    /**
     * 디렉토리 재귀 삭제
     */
    public void deleteDirectory(Path directory) {
        try {
            if (Files.exists(directory)) {
                Files.walkFileTree(directory, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    }
                });
                log.info("디렉토리 삭제 완료: {}", directory);
            }
        } catch (IOException e) {
            log.error("디렉토리 삭제 실패: {}", directory, e);
        }
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
                // CUSTOM인 경우 고객사 코드 사용
                String customerCode = hotfixBaseVersion.getCustomer() != null
                        ? hotfixBaseVersion.getCustomer().getCustomerCode()
                        : "unknown";
                basePath = String.format("versions/%s/custom/%s/%s/%s/hotfix/%d",
                        projectId,
                        customerCode,
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

        ReleaseVersion hotfixBaseVersion = hotfixVersion.getHotfixBaseVersion();
        String projectId = hotfixBaseVersion.getProject() != null ? hotfixBaseVersion.getProject().getProjectId() : "infraeye2";
        Path hotfixPath;

        if ("STANDARD".equals(hotfixBaseVersion.getReleaseType())) {
            hotfixPath = Paths.get(baseReleasePath, "versions", projectId, "standard",
                    hotfixBaseVersion.getMajorMinor(), hotfixBaseVersion.getVersion(),
                    "hotfix", String.valueOf(hotfixVersion.getHotfixVersion()));
        } else {
            String customerCode = hotfixBaseVersion.getCustomer() != null
                    ? hotfixBaseVersion.getCustomer().getCustomerCode()
                    : "unknown";
            hotfixPath = Paths.get(baseReleasePath, "versions", projectId, "custom",
                    customerCode, hotfixBaseVersion.getMajorMinor(), hotfixBaseVersion.getVersion(),
                    "hotfix", String.valueOf(hotfixVersion.getHotfixVersion()));
        }

        if (Files.exists(hotfixPath)) {
            deleteDirectory(hotfixPath);
            log.info("핫픽스 디렉토리 삭제 완료: {}", hotfixPath);

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
        }
    }

    /**
     * 빌드 디렉토리 베이스 경로 계산 (생성하지 않음)
     *
     * <pre>
     * STANDARD: versions/{projectId}/standard/{majorMinor}/{version}/builds/{buildVersion}
     * CUSTOM:   versions/{projectId}/custom/{customerCode}/{majorMinor}/{version}/builds/{buildVersion}
     * </pre>
     *
     * @param baseVersion  빌드의 원본 버전 (STANDARD 또는 CUSTOM)
     * @param buildVersion 빌드 버전 번호 (예: 260427)
     * @return 빌드 디렉토리 경로
     */
    public Path resolveBuildBasePath(ReleaseVersion baseVersion, Integer buildVersion) {
        String projectId = baseVersion.getProject() != null ? baseVersion.getProject().getProjectId() : "infraeye2";

        if ("STANDARD".equals(baseVersion.getReleaseType())) {
            return Paths.get(baseReleasePath, "versions", projectId, "standard",
                    baseVersion.getMajorMinor(), baseVersion.getVersion(),
                    "builds", String.valueOf(buildVersion));
        }

        String customerCode = baseVersion.getCustomer() != null
                ? baseVersion.getCustomer().getCustomerCode()
                : "unknown";
        return Paths.get(baseReleasePath, "versions", projectId, "custom",
                customerCode, baseVersion.getMajorMinor(), baseVersion.getVersion(),
                "builds", String.valueOf(buildVersion));
    }

    /**
     * 빌드 디렉토리 구조 생성
     *
     * <pre>
     * versions/{...}/builds/{buildVersion}/web/
     * versions/{...}/builds/{buildVersion}/engine/
     * versions/{...}/builds/{buildVersion}/etc/
     * </pre>
     *
     * @param buildVersion 빌드 버전 엔티티
     * @param baseVersion  빌드 원본 버전 엔티티
     */
    public void createBuildDirectoryStructure(ReleaseVersion buildVersion, ReleaseVersion baseVersion) {
        Path buildBase = resolveBuildBasePath(baseVersion, buildVersion.getBuildVersion());
        try {
            Files.createDirectories(buildBase.resolve("web"));
            Files.createDirectories(buildBase.resolve("engine"));
            Files.createDirectories(buildBase.resolve("etc"));
            log.info("빌드 디렉토리 구조 생성 완료: {}", buildBase);
        } catch (IOException e) {
            log.error("빌드 디렉토리 생성 실패: {}", buildVersion.getFullVersion(), e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "빌드 디렉토리 생성 실패: " + e.getMessage());
        }
    }

    /**
     * 빌드 카테고리(web/engine/etc) 경로 반환
     *
     * @param baseVersion  빌드 원본 버전
     * @param buildVersion 빌드 버전 번호
     * @param category     "web", "engine", "etc" 중 하나
     * @return 카테고리 경로
     */
    public Path resolveBuildCategoryPath(ReleaseVersion baseVersion, Integer buildVersion, String category) {
        if (!"web".equals(category) && !"engine".equals(category) && !"etc".equals(category)) {
            throw new IllegalArgumentException("빌드 카테고리는 web, engine, etc 중 하나여야 합니다: " + category);
        }
        return resolveBuildBasePath(baseVersion, buildVersion).resolve(category);
    }

    /**
     * 빌드 디렉토리 삭제
     *
     * @param buildVersion 빌드 버전 엔티티 (buildBaseVersion 이 채워져 있어야 함)
     */
    public void deleteBuildDirectory(ReleaseVersion buildVersion) {
        if (buildVersion.getBuildBaseVersion() == null) {
            log.warn("빌드의 원본 버전이 없습니다: {}", buildVersion.getReleaseVersionId());
            return;
        }

        Path buildPath = resolveBuildBasePath(buildVersion.getBuildBaseVersion(), buildVersion.getBuildVersion());

        if (Files.exists(buildPath)) {
            deleteDirectory(buildPath);
            log.info("빌드 디렉토리 삭제 완료: {}", buildPath);

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
        }
    }
}
