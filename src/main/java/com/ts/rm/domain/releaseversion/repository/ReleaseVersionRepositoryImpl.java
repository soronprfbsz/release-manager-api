package com.ts.rm.domain.releaseversion.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ts.rm.domain.releaseversion.entity.QReleaseVersion;
import com.ts.rm.domain.releaseversion.entity.ReleaseVersion;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * ReleaseVersion Repository Custom Implementation
 *
 * <p>QueryDSL을 사용한 복잡한 쿼리 구현
 */
@Repository
@RequiredArgsConstructor
public class ReleaseVersionRepositoryImpl implements ReleaseVersionRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<ReleaseVersion> findVersionsBetween(String projectId, String releaseType,
            String fromVersion, String toVersion) {
        QReleaseVersion rv = QReleaseVersion.releaseVersion;

        // From 버전 파싱
        String[] fromParts = fromVersion.split("\\.");
        int fromMajor = Integer.parseInt(fromParts[0]);
        int fromMinor = Integer.parseInt(fromParts[1]);
        int fromPatch = Integer.parseInt(fromParts[2]);

        // To 버전 파싱
        String[] toParts = toVersion.split("\\.");
        int toMajor = Integer.parseInt(toParts[0]);
        int toMinor = Integer.parseInt(toParts[1]);
        int toPatch = Integer.parseInt(toParts[2]);

        return queryFactory
                .selectFrom(rv)
                .where(rv.project.projectId.eq(projectId)  // 프로젝트 ID 필터링 추가
                        .and(rv.releaseType.eq(releaseType))
                        .and(rv.hotfixVersion.eq(0))  // 핫픽스 제외 (패치 생성에서 핫픽스 미포함)
                        .and(
                                // fromVersion <= version <= toVersion
                                rv.majorVersion.gt(fromMajor)
                                        .or(rv.majorVersion.eq(fromMajor)
                                                .and(rv.minorVersion.gt(fromMinor)))
                                        .or(rv.majorVersion.eq(fromMajor)
                                                .and(rv.minorVersion.eq(fromMinor))
                                                .and(rv.patchVersion.goe(fromPatch)))
                        )
                        .and(
                                // version <= toVersion
                                rv.majorVersion.lt(toMajor)
                                        .or(rv.majorVersion.eq(toMajor)
                                                .and(rv.minorVersion.lt(toMinor)))
                                        .or(rv.majorVersion.eq(toMajor)
                                                .and(rv.minorVersion.eq(toMinor))
                                                .and(rv.patchVersion.loe(toPatch)))
                        )
                )
                .orderBy(
                        rv.majorVersion.asc(),
                        rv.minorVersion.asc(),
                        rv.patchVersion.asc(),
                        rv.buildVersion.asc().nullsFirst()
                )
                .fetch();
    }

    @Override
    public Optional<ReleaseVersion> findLatestByProjectIdAndReleaseType(String projectId,
            String releaseType) {
        QReleaseVersion rv = QReleaseVersion.releaseVersion;

        ReleaseVersion result = queryFactory
                .selectFrom(rv)
                .where(
                        rv.project.projectId.eq(projectId),
                        rv.releaseType.eq(releaseType)
                )
                .orderBy(rv.createdAt.desc())
                .limit(1)
                .fetchOne();

        return Optional.ofNullable(result);
    }

    @Override
    public List<ReleaseVersion> findRecentByProjectIdAndReleaseType(String projectId,
            String releaseType, int limit) {
        QReleaseVersion rv = QReleaseVersion.releaseVersion;

        return queryFactory
                .selectFrom(rv)
                .where(
                        rv.project.projectId.eq(projectId),
                        rv.releaseType.eq(releaseType),
                        rv.hotfixVersion.eq(0),  // 핫픽스 제외
                        rv.buildVersion.eq(0)   // 빌드 버전 제외 (정식 릴리즈만)
                )
                // "최신 버전" = 버전 번호 최고값 (생성 시각이 아님 — createdAt 역전 데이터에서 오답 방지)
                .orderBy(rv.majorVersion.desc(), rv.minorVersion.desc(), rv.patchVersion.desc(),
                        rv.createdAt.desc())
                .limit(limit)
                .fetch();
    }

    @Override
    public List<ReleaseVersion> findRecentBuildsByProjectId(String projectId, int limit) {
        QReleaseVersion rv = QReleaseVersion.releaseVersion;

        return queryFactory
                .selectFrom(rv)
                .where(
                        rv.project.projectId.eq(projectId),
                        rv.buildVersion.gt(0)  // 빌드 행만
                )
                .orderBy(rv.createdAt.desc())
                .limit(limit)
                .fetch();
    }

    @Override
    public List<ReleaseVersion> findUnapprovedVersionsBetween(String projectId, String releaseType,
            String fromVersion, String toVersion) {
        QReleaseVersion rv = QReleaseVersion.releaseVersion;

        // From 버전 파싱
        String[] fromParts = fromVersion.split("\\.");
        int fromMajor = Integer.parseInt(fromParts[0]);
        int fromMinor = Integer.parseInt(fromParts[1]);
        int fromPatch = Integer.parseInt(fromParts[2]);

        // To 버전 파싱
        String[] toParts = toVersion.split("\\.");
        int toMajor = Integer.parseInt(toParts[0]);
        int toMinor = Integer.parseInt(toParts[1]);
        int toPatch = Integer.parseInt(toParts[2]);

        return queryFactory
                .selectFrom(rv)
                .where(rv.project.projectId.eq(projectId)  // 프로젝트 ID 필터링 추가
                        .and(rv.releaseType.eq(releaseType))
                        .and(rv.hotfixVersion.eq(0))  // 핫픽스 제외 (핫픽스는 별도 승인 처리)
                        .and(rv.isApproved.isFalse())  // 미승인 버전만 조회
                        .and(
                                // fromVersion <= version <= toVersion
                                rv.majorVersion.gt(fromMajor)
                                        .or(rv.majorVersion.eq(fromMajor)
                                                .and(rv.minorVersion.gt(fromMinor)))
                                        .or(rv.majorVersion.eq(fromMajor)
                                                .and(rv.minorVersion.eq(fromMinor))
                                                .and(rv.patchVersion.goe(fromPatch)))
                        )
                        .and(
                                // version <= toVersion
                                rv.majorVersion.lt(toMajor)
                                        .or(rv.majorVersion.eq(toMajor)
                                                .and(rv.minorVersion.lt(toMinor)))
                                        .or(rv.majorVersion.eq(toMajor)
                                                .and(rv.minorVersion.eq(toMinor))
                                                .and(rv.patchVersion.loe(toPatch)))
                        )
                )
                .orderBy(
                        rv.majorVersion.asc(),
                        rv.minorVersion.asc(),
                        rv.patchVersion.asc(),
                        rv.buildVersion.asc().nullsFirst()
                )
                .fetch();
    }

    @Override
    public List<ReleaseVersion> findCustomVersionsBetween(Long siteId, String fromVersion,
            String toVersion) {
        QReleaseVersion rv = QReleaseVersion.releaseVersion;

        // From 버전 파싱
        String[] fromParts = fromVersion.split("\\.");
        int fromMajor = Integer.parseInt(fromParts[0]);
        int fromMinor = Integer.parseInt(fromParts[1]);
        int fromPatch = Integer.parseInt(fromParts[2]);

        // To 버전 파싱
        String[] toParts = toVersion.split("\\.");
        int toMajor = Integer.parseInt(toParts[0]);
        int toMinor = Integer.parseInt(toParts[1]);
        int toPatch = Integer.parseInt(toParts[2]);

        return queryFactory
                .selectFrom(rv)
                .where(rv.releaseType.eq("CUSTOM")
                        .and(rv.site.siteId.eq(siteId))
                        .and(rv.hotfixVersion.eq(0))  // 핫픽스 제외 (패치 생성에서 핫픽스 미포함)
                        .and(
                                // fromVersion <= version <= toVersion (커스텀 버전 기준)
                                rv.customMajorVersion.gt(fromMajor)
                                        .or(rv.customMajorVersion.eq(fromMajor)
                                                .and(rv.customMinorVersion.gt(fromMinor)))
                                        .or(rv.customMajorVersion.eq(fromMajor)
                                                .and(rv.customMinorVersion.eq(fromMinor))
                                                .and(rv.customPatchVersion.goe(fromPatch)))
                        )
                        .and(
                                // version <= toVersion (커스텀 버전 기준)
                                rv.customMajorVersion.lt(toMajor)
                                        .or(rv.customMajorVersion.eq(toMajor)
                                                .and(rv.customMinorVersion.lt(toMinor)))
                                        .or(rv.customMajorVersion.eq(toMajor)
                                                .and(rv.customMinorVersion.eq(toMinor))
                                                .and(rv.customPatchVersion.loe(toPatch)))
                        )
                )
                .orderBy(
                        rv.customMajorVersion.asc(),
                        rv.customMinorVersion.asc(),
                        rv.customPatchVersion.asc(),
                        rv.buildVersion.asc().nullsFirst()
                )
                .fetch();
    }

    @Override
    public List<ReleaseVersion> findUnapprovedCustomVersionsBetween(Long siteId,
            String fromVersion, String toVersion) {
        QReleaseVersion rv = QReleaseVersion.releaseVersion;

        // From 버전 파싱
        String[] fromParts = fromVersion.split("\\.");
        int fromMajor = Integer.parseInt(fromParts[0]);
        int fromMinor = Integer.parseInt(fromParts[1]);
        int fromPatch = Integer.parseInt(fromParts[2]);

        // To 버전 파싱
        String[] toParts = toVersion.split("\\.");
        int toMajor = Integer.parseInt(toParts[0]);
        int toMinor = Integer.parseInt(toParts[1]);
        int toPatch = Integer.parseInt(toParts[2]);

        return queryFactory
                .selectFrom(rv)
                .where(rv.releaseType.eq("CUSTOM")
                        .and(rv.site.siteId.eq(siteId))
                        .and(rv.hotfixVersion.eq(0))  // 핫픽스 제외 (핫픽스는 별도 승인 처리)
                        .and(rv.isApproved.isFalse())  // 미승인 버전만 조회
                        .and(
                                // fromVersion <= version <= toVersion (커스텀 버전 기준)
                                rv.customMajorVersion.gt(fromMajor)
                                        .or(rv.customMajorVersion.eq(fromMajor)
                                                .and(rv.customMinorVersion.gt(fromMinor)))
                                        .or(rv.customMajorVersion.eq(fromMajor)
                                                .and(rv.customMinorVersion.eq(fromMinor))
                                                .and(rv.customPatchVersion.goe(fromPatch)))
                        )
                        .and(
                                // version <= toVersion (커스텀 버전 기준)
                                rv.customMajorVersion.lt(toMajor)
                                        .or(rv.customMajorVersion.eq(toMajor)
                                                .and(rv.customMinorVersion.lt(toMinor)))
                                        .or(rv.customMajorVersion.eq(toMajor)
                                                .and(rv.customMinorVersion.eq(toMinor))
                                                .and(rv.customPatchVersion.loe(toPatch)))
                        )
                )
                .orderBy(
                        rv.customMajorVersion.asc(),
                        rv.customMinorVersion.asc(),
                        rv.customPatchVersion.asc()
                )
                .fetch();
    }

    @Override
    public List<Long> findSiteIdsWithCustomVersions(String projectId) {
        QReleaseVersion rv = QReleaseVersion.releaseVersion;

        return queryFactory
                .select(rv.site.siteId)
                .distinct()
                .from(rv)
                .where(rv.releaseType.eq("CUSTOM")
                        .and(rv.project.projectId.eq(projectId))
                        .and(rv.site.isNotNull()))
                .fetch();
    }

    // ========================================
    // Hotfix 관련 메서드 구현
    // ========================================

    @Override
    public List<ReleaseVersion> findVersionsBetweenExcludingHotfixes(String projectId, String releaseType,
            String fromVersion, String toVersion) {
        QReleaseVersion rv = QReleaseVersion.releaseVersion;

        // From 버전 파싱
        String[] fromParts = fromVersion.split("\\.");
        int fromMajor = Integer.parseInt(fromParts[0]);
        int fromMinor = Integer.parseInt(fromParts[1]);
        int fromPatch = Integer.parseInt(fromParts[2]);

        // To 버전 파싱
        String[] toParts = toVersion.split("\\.");
        int toMajor = Integer.parseInt(toParts[0]);
        int toMinor = Integer.parseInt(toParts[1]);
        int toPatch = Integer.parseInt(toParts[2]);

        return queryFactory
                .selectFrom(rv)
                .where(rv.project.projectId.eq(projectId)
                        .and(rv.releaseType.eq(releaseType))
                        .and(rv.hotfixVersion.eq(0))  // 핫픽스 제외
                        .and(
                                // fromVersion <= version <= toVersion
                                rv.majorVersion.gt(fromMajor)
                                        .or(rv.majorVersion.eq(fromMajor)
                                                .and(rv.minorVersion.gt(fromMinor)))
                                        .or(rv.majorVersion.eq(fromMajor)
                                                .and(rv.minorVersion.eq(fromMinor))
                                                .and(rv.patchVersion.goe(fromPatch)))
                        )
                        .and(
                                // version <= toVersion
                                rv.majorVersion.lt(toMajor)
                                        .or(rv.majorVersion.eq(toMajor)
                                                .and(rv.minorVersion.lt(toMinor)))
                                        .or(rv.majorVersion.eq(toMajor)
                                                .and(rv.minorVersion.eq(toMinor))
                                                .and(rv.patchVersion.loe(toPatch)))
                        )
                )
                .orderBy(
                        rv.majorVersion.asc(),
                        rv.minorVersion.asc(),
                        rv.patchVersion.asc()
                )
                .fetch();
    }

    @Override
    public List<ReleaseVersion> findBuildsInBaseRange(String projectId, Long fromBaseId, Long toBaseId, Long siteId) {
        QReleaseVersion rv = QReleaseVersion.releaseVersion;

        BooleanExpression projectMatch = rv.project.projectId.eq(projectId);
        BooleanExpression isBuild = rv.buildVersion.gt(0);
        BooleanExpression baseRange = rv.buildBaseVersion.releaseVersionId.goe(fromBaseId)
                .and(rv.buildBaseVersion.releaseVersionId.loe(toBaseId));
        BooleanExpression siteMatch = (siteId == null)
                ? rv.site.isNull()
                : rv.site.siteId.eq(siteId);

        return queryFactory
                .selectFrom(rv)
                .where(projectMatch, isBuild, baseRange, siteMatch)
                // build_version DESC + 같은 날짜 내에서는 build_iteration DESC.
                // (iteration 정렬이 빠지면 같은 build_version 의 -1/-2 순서가 PK 자연 순서로 결정되어
                //  picker UI 가 -1 을 latest 로 잘못 표시한다)
                .orderBy(rv.buildVersion.desc(), rv.buildIteration.desc())
                .fetch();
    }

    @Override
    public List<ReleaseVersion> findHotfixesInBaseRange(String projectId, Long fromBaseId, Long toBaseId, Long siteId) {
        QReleaseVersion rv = QReleaseVersion.releaseVersion;

        BooleanExpression projectMatch = rv.project.projectId.eq(projectId);
        BooleanExpression isHotfix = rv.hotfixVersion.gt(0);
        BooleanExpression baseRange = rv.hotfixBaseVersion.releaseVersionId.goe(fromBaseId)
                .and(rv.hotfixBaseVersion.releaseVersionId.loe(toBaseId));
        BooleanExpression siteMatch = (siteId == null)
                ? rv.site.isNull()
                : rv.site.siteId.eq(siteId);

        return queryFactory
                .selectFrom(rv)
                .where(projectMatch, isHotfix, baseRange, siteMatch)
                .orderBy(rv.hotfixVersion.asc())
                .fetch();
    }

    @Override
    public Integer findMaxHotfixVersionByHotfixBaseVersionId(Long hotfixBaseVersionId) {
        QReleaseVersion rv = QReleaseVersion.releaseVersion;

        Integer maxHotfixVersion = queryFactory
                .select(rv.hotfixVersion.max())
                .from(rv)
                .where(rv.hotfixBaseVersion.releaseVersionId.eq(hotfixBaseVersionId))
                .fetchOne();

        return maxHotfixVersion != null ? maxHotfixVersion : 0;
    }

    @Override
    public Long countHotfixesByHotfixBaseVersionId(Long hotfixBaseVersionId) {
        QReleaseVersion rv = QReleaseVersion.releaseVersion;

        Long count = queryFactory
                .select(rv.count())
                .from(rv)
                .where(rv.hotfixBaseVersion.releaseVersionId.eq(hotfixBaseVersionId))
                .fetchOne();

        return count != null ? count : 0L;
    }

    @Override
    public List<Long> findVersionIdsWithHotfixes(String projectId, String releaseType) {
        QReleaseVersion rv = QReleaseVersion.releaseVersion;

        return queryFactory
                .select(rv.hotfixBaseVersion.releaseVersionId)
                .distinct()
                .from(rv)
                .where(rv.project.projectId.eq(projectId)
                        .and(rv.releaseType.eq(releaseType))
                        .and(rv.hotfixBaseVersion.isNotNull())
                        .and(rv.hotfixVersion.gt(0)))
                .fetch();
    }

    @Override
    public Optional<Integer> findMaxBuildVersionByBaseId(Long buildBaseVersionId) {
        QReleaseVersion rv = QReleaseVersion.releaseVersion;

        Integer max = queryFactory
                .select(rv.buildVersion.max())
                .from(rv)
                .where(rv.buildBaseVersion.releaseVersionId.eq(buildBaseVersionId))
                .fetchOne();

        // max() 자체는 결과가 없을 때 null 을 반환. Optional 로 감싸 호출자에 명시적으로 전달.
        return Optional.ofNullable(max);
    }
}
