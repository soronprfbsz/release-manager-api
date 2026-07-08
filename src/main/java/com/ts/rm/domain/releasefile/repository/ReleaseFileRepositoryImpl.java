package com.ts.rm.domain.releasefile.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ts.rm.domain.releasefile.entity.QReleaseFile;
import com.ts.rm.domain.releasefile.entity.ReleaseFile;
import com.ts.rm.domain.releasefile.enums.FileCategory;
import com.ts.rm.domain.releaseversion.entity.QReleaseVersion;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * ReleaseFile Repository Custom Implementation
 *
 * <p>QueryDSL을 사용한 복잡한 쿼리 구현
 */
@Repository
@RequiredArgsConstructor
public class ReleaseFileRepositoryImpl implements ReleaseFileRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<ReleaseFile> findReleaseFilesBetweenVersions(String projectId, String fromVersion, String toVersion) {
        QReleaseFile rf = QReleaseFile.releaseFile;

        return queryFactory
                .selectFrom(rf)
                .where(
                        rf.releaseVersion.project.projectId.eq(projectId),
                        versionBetween(rf.releaseVersion, fromVersion, toVersion),
                        rf.releaseVersion.hotfixVersion.eq(0)  // 핫픽스 제외 (패치 생성 시 핫픽스 파일 미포함)
                )
                .orderBy(
                        rf.releaseVersion.majorVersion.asc(),
                        rf.releaseVersion.minorVersion.asc(),
                        rf.releaseVersion.patchVersion.asc(),
                        rf.executionOrder.asc()
                )
                .fetch();
    }

    @Override
    public List<ReleaseFile> findReleaseFilesBetweenVersionsBySubCategory(String projectId, String fromVersion, String toVersion, String subCategory) {
        QReleaseFile rf = QReleaseFile.releaseFile;

        return queryFactory
                .selectFrom(rf)
                .where(
                        rf.releaseVersion.project.projectId.eq(projectId),  // 프로젝트 ID 필터링 추가
                        versionBetween(rf.releaseVersion, fromVersion, toVersion),
                        rf.releaseVersion.hotfixVersion.eq(0),  // 핫픽스 제외 (패치 생성 시 핫픽스 파일 미포함)
                        rf.subCategory.equalsIgnoreCase(subCategory)
                )
                .orderBy(
                        rf.releaseVersion.majorVersion.asc(),
                        rf.releaseVersion.minorVersion.asc(),
                        rf.releaseVersion.patchVersion.asc(),
                        rf.executionOrder.asc()
                )
                .fetch();
    }

    @Override
    public List<ReleaseFile> findBuildArtifactsBetweenVersions(String projectId, String fromVersion, String toVersion) {
        QReleaseFile rf = QReleaseFile.releaseFile;

        return queryFactory
                .selectFrom(rf)
                .where(
                        rf.releaseVersion.project.projectId.eq(projectId),  // 프로젝트 ID 필터링 추가
                        versionBetween(rf.releaseVersion, fromVersion, toVersion),
                        rf.releaseVersion.hotfixVersion.eq(0),  // 핫픽스 제외 (패치 생성 시 핫픽스 파일 미포함)
                        rf.fileCategory.in(FileCategory.WEB, FileCategory.ENGINE)
                )
                .orderBy(
                        rf.releaseVersion.majorVersion.desc(),
                        rf.releaseVersion.minorVersion.desc(),
                        rf.releaseVersion.patchVersion.desc(),
                        rf.executionOrder.asc()
                )
                .fetch();
    }

    @Override
    public List<FileCategory> findCategoriesByVersionId(Long releaseVersionId) {
        QReleaseFile rf = QReleaseFile.releaseFile;

        return queryFactory
                .selectDistinct(rf.fileCategory)
                .from(rf)
                .where(
                        rf.releaseVersion.releaseVersionId.eq(releaseVersionId),
                        rf.fileCategory.isNotNull()
                )
                .orderBy(rf.fileCategory.asc())
                .fetch();
    }

    /**
     * {@code fromVersion <= version <= toVersion} 범위 조건 (major.minor.patch 정수 비교).
     *
     * <p>{@code version} 은 VARCHAR 이라 그대로 비교하면 사전식(lexicographic) 정렬이 되어
     * {@code 1.1.5 → 1.1.12} 처럼 patch 번호가 한 자리→두 자리로 넘어가는 범위에서 결과가
     * 뒤집혀 0건이 되던 버그가 있었다. {@code ReleaseVersion} 의 정수 컬럼으로 비교하여
     * {@code ReleaseVersionRepositoryImpl#findVersionsBetween} 과 동일한 숫자 순서를 보장한다.
     */
    private BooleanExpression versionBetween(QReleaseVersion rv, String fromVersion, String toVersion) {
        int[] from = parseVersion(fromVersion);
        int[] to = parseVersion(toVersion);

        // fromVersion <= version
        BooleanExpression geFrom = rv.majorVersion.gt(from[0])
                .or(rv.majorVersion.eq(from[0]).and(rv.minorVersion.gt(from[1])))
                .or(rv.majorVersion.eq(from[0]).and(rv.minorVersion.eq(from[1]))
                        .and(rv.patchVersion.goe(from[2])));

        // version <= toVersion
        BooleanExpression leTo = rv.majorVersion.lt(to[0])
                .or(rv.majorVersion.eq(to[0]).and(rv.minorVersion.lt(to[1])))
                .or(rv.majorVersion.eq(to[0]).and(rv.minorVersion.eq(to[1]))
                        .and(rv.patchVersion.loe(to[2])));

        return geFrom.and(leTo);
    }

    /**
     * "major.minor.patch" 문자열을 정수 3개로 파싱한다.
     * (커스텀 접미사가 붙어도 앞 3개 세그먼트만 사용 — findVersionsBetween 과 동일 정책)
     */
    private int[] parseVersion(String version) {
        String[] parts = version.split("\\.");
        return new int[]{
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2])
        };
    }
}
