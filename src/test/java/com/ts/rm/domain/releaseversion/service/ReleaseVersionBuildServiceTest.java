package com.ts.rm.domain.releaseversion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.ts.rm.domain.account.entity.Account;
import com.ts.rm.domain.releasefile.repository.ReleaseFileRepository;
import com.ts.rm.domain.releaseversion.dto.ReleaseVersionDto;
import com.ts.rm.domain.releaseversion.entity.ReleaseVersion;
import com.ts.rm.domain.releaseversion.mapper.ReleaseVersionDtoMapper;
import com.ts.rm.domain.releaseversion.repository.ReleaseVersionHierarchyRepository;
import com.ts.rm.domain.releaseversion.repository.ReleaseVersionRepository;
import com.ts.rm.global.account.AccountLookupService;
import com.ts.rm.global.exception.BusinessException;
import com.ts.rm.global.exception.ErrorCode;
import java.util.ArrayList;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * ReleaseVersionService 의 빌드 버전 관련 단위 테스트.
 *
 * <p>기존 ReleaseVersionServiceTest 가 stale import (ReleaseCategory) 로 컴파일 실패
 * 상태이므로, 빌드 기능만 별도 파일에서 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReleaseVersionService 빌드 버전 테스트")
class ReleaseVersionBuildServiceTest {

    @Mock
    private ReleaseVersionRepository releaseVersionRepository;

    @Mock
    private ReleaseFileRepository releaseFileRepository;

    @Mock
    private ReleaseVersionHierarchyRepository hierarchyRepository;

    @Mock
    private com.ts.rm.domain.customer.repository.CustomerRepository customerRepository;

    @Mock
    private com.ts.rm.domain.project.repository.ProjectRepository projectRepository;

    @Mock
    private AccountLookupService accountLookupService;

    @Mock
    private ReleaseVersionDtoMapper mapper;

    @Mock
    private ReleaseVersionFileSystemService fileSystemService;

    @Mock
    private ReleaseVersionTreeService treeService;

    @InjectMocks
    private ReleaseVersionService releaseVersionService;

    private ReleaseVersion buildBaseStandard() {
        return ReleaseVersion.builder()
                .releaseVersionId(10L)
                .releaseType("STANDARD")
                .version("1.1.0")
                .majorVersion(1).minorVersion(1).patchVersion(0)
                .hotfixVersion(0).buildVersion(0)
                .releaseFiles(new ArrayList<>())
                .build();
    }

    private Account creator() {
        return Account.builder()
                .accountId(99L)
                .email("jhlee@tscientific")
                .accountName("이지환")
                .build();
    }

    @Test
    @DisplayName("빌드 생성 - 명시된 buildVersion 으로 정상 생성 (즉시 활성)")
    void createBuild_explicitBuildVersion_success() {
        ReleaseVersion base = buildBaseStandard();
        Account c = creator();
        ReleaseVersionDto.CreateBuildRequest req = ReleaseVersionDto.CreateBuildRequest.builder()
                .comment("WEB 빌드")
                .buildVersion(260427)
                .build();

        given(releaseVersionRepository.findById(10L)).willReturn(Optional.of(base));
        given(accountLookupService.findByEmail("jhlee@tscientific")).willReturn(c);
        given(releaseVersionRepository
                .existsByBuildBaseVersion_ReleaseVersionIdAndBuildVersion(10L, 260427))
                .willReturn(false);
        given(releaseVersionRepository.saveAndFlush(any(ReleaseVersion.class)))
                .willAnswer(inv -> {
                    ReleaseVersion v = inv.getArgument(0);
                    v.setReleaseVersionId(99L);
                    return v;
                });

        ReleaseVersionDto.CreateBuildResponse result = releaseVersionService
                .createBuild(10L, req, "jhlee@tscientific");

        assertThat(result).isNotNull();
        assertThat(result.buildVersionId()).isEqualTo(99L);
        assertThat(result.buildVersion()).isEqualTo(260427);
        assertThat(result.version()).isEqualTo("1.1.0");
        assertThat(result.fullVersion()).isEqualTo("1.1.0.260427");
        then(releaseVersionRepository).should(times(1)).saveAndFlush(any(ReleaseVersion.class));
        then(fileSystemService).should(times(1))
                .createBuildDirectoryStructure(any(ReleaseVersion.class), any(ReleaseVersion.class));
    }

    @Test
    @DisplayName("빌드 생성 - 같은 build_version 이미 있으면 +1 자동 증가하여 생성")
    void createBuild_collisionRetry_incrementsBuildVersion() {
        ReleaseVersion base = buildBaseStandard();
        Account c = creator();
        ReleaseVersionDto.CreateBuildRequest req = ReleaseVersionDto.CreateBuildRequest.builder()
                .comment("WEB 빌드")
                .buildVersion(260427)
                .build();

        given(releaseVersionRepository.findById(10L)).willReturn(Optional.of(base));
        given(accountLookupService.findByEmail("jhlee@tscientific")).willReturn(c);
        // 260427 충돌, 260428 가능
        given(releaseVersionRepository
                .existsByBuildBaseVersion_ReleaseVersionIdAndBuildVersion(10L, 260427))
                .willReturn(true);
        given(releaseVersionRepository
                .existsByBuildBaseVersion_ReleaseVersionIdAndBuildVersion(10L, 260428))
                .willReturn(false);
        given(releaseVersionRepository.saveAndFlush(any(ReleaseVersion.class)))
                .willAnswer(inv -> {
                    ReleaseVersion v = inv.getArgument(0);
                    v.setReleaseVersionId(100L);
                    return v;
                });

        ReleaseVersionDto.CreateBuildResponse result = releaseVersionService
                .createBuild(10L, req, "jhlee@tscientific");

        // 260427 → 260428 로 자동 증가
        assertThat(result.buildVersion()).isEqualTo(260428);
        assertThat(result.fullVersion()).isEqualTo("1.1.0.260428");
    }

    @Test
    @DisplayName("빌드 생성 - 핫픽스 버전 위에는 빌드 생성 거부")
    void createBuild_onHotfix_rejected() {
        ReleaseVersion hotfixBase = ReleaseVersion.builder()
                .releaseVersionId(20L)
                .releaseType("STANDARD")
                .version("1.1.0")
                .majorVersion(1).minorVersion(1).patchVersion(0)
                .hotfixVersion(1)  // 핫픽스
                .buildVersion(0)
                .releaseFiles(new ArrayList<>())
                .build();

        ReleaseVersionDto.CreateBuildRequest req = ReleaseVersionDto.CreateBuildRequest.builder()
                .comment("X").buildVersion(260427).build();

        given(releaseVersionRepository.findById(20L)).willReturn(Optional.of(hotfixBase));

        assertThatThrownBy(() -> releaseVersionService.createBuild(20L, req, "jhlee@tscientific"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);

        then(releaseVersionRepository).should(never()).saveAndFlush(any(ReleaseVersion.class));
    }

    @Test
    @DisplayName("빌드 생성 - 빌드 버전 위에는 빌드 생성 거부")
    void createBuild_onBuild_rejected() {
        ReleaseVersion buildBase = ReleaseVersion.builder()
                .releaseVersionId(30L)
                .releaseType("STANDARD")
                .version("1.1.0")
                .majorVersion(1).minorVersion(1).patchVersion(0)
                .hotfixVersion(0)
                .buildVersion(260427)  // 이미 빌드
                .releaseFiles(new ArrayList<>())
                .build();

        ReleaseVersionDto.CreateBuildRequest req = ReleaseVersionDto.CreateBuildRequest.builder()
                .comment("X").buildVersion(260428).build();

        given(releaseVersionRepository.findById(30L)).willReturn(Optional.of(buildBase));

        assertThatThrownBy(() -> releaseVersionService.createBuild(30L, req, "jhlee@tscientific"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);

        then(releaseVersionRepository).should(never()).saveAndFlush(any(ReleaseVersion.class));
    }

    @Test
    @DisplayName("todayYyMmDd - 6자리 정수 (yyMMdd) 반환 검증")
    void todayYyMmDd_returnsValidYymmdd() {
        int v = ReleaseVersionService.todayYyMmDd();
        // 6자리: 100000 ≤ v ≤ 999999 (yy=10~99 가정. 26 인 현 시점 기준 260000+)
        assertThat(v).isBetween(100000, 999999);
        // yy 부분이 합리적 범위인지 (현재 26~30 사이일 것)
        int yy = v / 10000;
        assertThat(yy).isBetween(20, 99);
    }
}
