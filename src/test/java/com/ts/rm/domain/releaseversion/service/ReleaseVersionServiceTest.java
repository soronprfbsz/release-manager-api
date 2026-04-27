package com.ts.rm.domain.releaseversion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.ts.rm.domain.account.entity.Account;
import com.ts.rm.domain.customer.entity.Customer;
import com.ts.rm.domain.customer.repository.CustomerRepository;
import com.ts.rm.domain.project.entity.Project;
import com.ts.rm.domain.project.repository.ProjectRepository;
import com.ts.rm.domain.releasefile.repository.ReleaseFileRepository;
import com.ts.rm.domain.releaseversion.dto.ReleaseVersionDto;
import com.ts.rm.domain.releaseversion.entity.ReleaseVersion;
import com.ts.rm.domain.releaseversion.mapper.ReleaseVersionDtoMapper;
import com.ts.rm.domain.releaseversion.repository.ReleaseVersionHierarchyRepository;
import com.ts.rm.domain.releaseversion.repository.ReleaseVersionRepository;
import com.ts.rm.global.account.AccountLookupService;
import com.ts.rm.global.exception.BusinessException;
import com.ts.rm.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * ReleaseVersionService 단위 테스트.
 *
 * <p>create/getById/getByType/update/delete/getVersionsBetween 등 일반 동작을 검증한다.
 * 빌드 버전 관련 로직은 {@link ReleaseVersionBuildServiceTest} 를 참조.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReleaseVersionService 테스트")
class ReleaseVersionServiceTest {

    @Mock
    private ReleaseVersionRepository releaseVersionRepository;

    @Mock
    private ReleaseFileRepository releaseFileRepository;

    @Mock
    private ReleaseVersionHierarchyRepository hierarchyRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private ProjectRepository projectRepository;

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

    private static final String PROJECT_ID = "infraeye2";

    private Project testProject;
    private Account testAccount;
    private Customer testCustomer;
    private ReleaseVersion testVersion;
    private ReleaseVersionDto.CreateRequest createRequest;
    private ReleaseVersionDto.DetailResponse detailResponse;

    @BeforeEach
    void setUp() {
        testProject = Project.builder()
                .projectId(PROJECT_ID)
                .projectName("Infraeye 2")
                .build();

        testAccount = Account.builder()
                .accountId(1L)
                .email("jhlee@tscientific")
                .accountName("이재훈")
                .build();

        testCustomer = Customer.builder()
                .customerId(1L)
                .customerCode("company_a")
                .customerName("A회사")
                .isActive(true)
                .build();

        testVersion = ReleaseVersion.builder()
                .releaseVersionId(1L)
                .project(testProject)
                .releaseType("STANDARD")
                .version("1.1.0")
                .majorVersion(1)
                .minorVersion(1)
                .patchVersion(0)
                .creator(testAccount)
                .createdByEmail("jhlee@tscientific")
                .comment("새로운 기능")
                .releaseFiles(new ArrayList<>())
                .build();

        createRequest = ReleaseVersionDto.CreateRequest.builder()
                .projectId(PROJECT_ID)
                .version("1.1.0")
                .createdByEmail("jhlee@tscientific")
                .comment("새로운 기능")
                .build();

        detailResponse = buildDetailResponse(1L, "1.1.0", 1, 1, 0);
    }

    @Test
    @DisplayName("표준 릴리즈 버전 생성 - 성공")
    void createStandardVersion_Success() {
        // given
        given(projectRepository.findById(PROJECT_ID)).willReturn(Optional.of(testProject));
        given(accountLookupService.findByEmail(anyString())).willReturn(testAccount);
        given(releaseVersionRepository.existsByProject_ProjectIdAndVersion(anyString(), anyString()))
                .willReturn(false);
        given(releaseVersionRepository.save(any(ReleaseVersion.class))).willReturn(testVersion);
        given(mapper.toDetailResponse(any(ReleaseVersion.class))).willReturn(detailResponse);

        // when
        ReleaseVersionDto.DetailResponse result = releaseVersionService.createStandardVersion(
                createRequest);

        // then
        assertThat(result).isNotNull();
        assertThat(result.version()).isEqualTo("1.1.0");
        assertThat(result.majorVersion()).isEqualTo(1);
        assertThat(result.minorVersion()).isEqualTo(1);
        assertThat(result.patchVersion()).isEqualTo(0);
        assertThat(result.majorMinor()).isEqualTo("1.1.x");

        then(releaseVersionRepository).should(times(1)).save(any(ReleaseVersion.class));
    }

    @Test
    @DisplayName("표준 릴리즈 버전 생성 - 중복 버전 실패")
    void createStandardVersion_DuplicateVersion() {
        // given
        given(projectRepository.findById(PROJECT_ID)).willReturn(Optional.of(testProject));
        given(accountLookupService.findByEmail(anyString())).willReturn(testAccount);
        given(releaseVersionRepository.existsByProject_ProjectIdAndVersion(anyString(), anyString()))
                .willReturn(true);

        // when & then
        assertThatThrownBy(() -> releaseVersionService.createStandardVersion(createRequest))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RELEASE_VERSION_CONFLICT);

        then(releaseVersionRepository).should(never()).save(any(ReleaseVersion.class));
    }

    @Test
    @DisplayName("표준 릴리즈 버전 생성 - 잘못된 버전 형식")
    void createStandardVersion_InvalidVersionFormat() {
        // given
        ReleaseVersionDto.CreateRequest invalidRequest = ReleaseVersionDto.CreateRequest.builder()
                .projectId(PROJECT_ID)
                .version("invalid")
                .createdByEmail("jhlee@tscientific")
                .comment("테스트")
                .build();

        given(projectRepository.findById(PROJECT_ID)).willReturn(Optional.of(testProject));
        given(accountLookupService.findByEmail(anyString())).willReturn(testAccount);

        // when & then
        assertThatThrownBy(() -> releaseVersionService.createStandardVersion(invalidRequest))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_VERSION_FORMAT);
    }

    @Test
    @DisplayName("커스텀 릴리즈 버전 생성 - 성공")
    void createCustomVersion_Success() {
        // given
        ReleaseVersionDto.CreateRequest customRequest = ReleaseVersionDto.CreateRequest.builder()
                .projectId(PROJECT_ID)
                .version("1.0.0")
                .createdByEmail("admin@tscientific")
                .comment("커스텀 버전")
                .customerId(1L)
                .customMajorVersion(1)
                .customMinorVersion(0)
                .customPatchVersion(0)
                .build();

        ReleaseVersion customVersion = ReleaseVersion.builder()
                .releaseVersionId(2L)
                .project(testProject)
                .releaseType("CUSTOM")
                .customer(testCustomer)
                .version("1.0.0")
                .majorVersion(1)
                .minorVersion(0)
                .patchVersion(0)
                .creator(testAccount)
                .createdByEmail("admin@tscientific")
                .comment("커스텀 버전")
                .releaseFiles(new ArrayList<>())
                .build();

        given(customerRepository.findById(anyLong())).willReturn(Optional.of(testCustomer));
        given(projectRepository.findById(PROJECT_ID)).willReturn(Optional.of(testProject));
        given(accountLookupService.findByEmail(anyString())).willReturn(testAccount);
        given(releaseVersionRepository.existsByProject_ProjectIdAndVersion(anyString(), anyString()))
                .willReturn(false);
        given(releaseVersionRepository.save(any(ReleaseVersion.class))).willReturn(customVersion);
        given(mapper.toDetailResponse(any(ReleaseVersion.class))).willReturn(detailResponse);

        // when
        ReleaseVersionDto.DetailResponse result = releaseVersionService.createCustomVersion(
                customRequest);

        // then
        assertThat(result).isNotNull();
        then(customerRepository).should(times(1)).findById(1L);
        then(releaseVersionRepository).should(times(1)).save(any(ReleaseVersion.class));
    }

    @Test
    @DisplayName("커스텀 릴리즈 버전 생성 - 고객사 ID 없음")
    void createCustomVersion_MissingCustomerId() {
        // given
        ReleaseVersionDto.CreateRequest invalidRequest = ReleaseVersionDto.CreateRequest.builder()
                .projectId(PROJECT_ID)
                .version("1.0.0")
                .createdByEmail("admin@tscientific")
                .comment("커스텀 버전")
                .customerId(null)
                .build();

        // when & then
        assertThatThrownBy(() -> releaseVersionService.createCustomVersion(invalidRequest))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CUSTOMER_ID_REQUIRED);
    }

    @Test
    @DisplayName("릴리즈 버전 조회 (ID) - 성공")
    void getVersionById_Success() {
        // given
        given(releaseVersionRepository.findById(anyLong())).willReturn(Optional.of(testVersion));
        given(mapper.toDetailResponse(any(ReleaseVersion.class))).willReturn(detailResponse);

        // when
        ReleaseVersionDto.DetailResponse result = releaseVersionService.getVersionById(1L);

        // then
        assertThat(result).isNotNull();
        assertThat(result.releaseVersionId()).isEqualTo(1L);

        then(releaseVersionRepository).should(times(1)).findById(1L);
    }

    @Test
    @DisplayName("릴리즈 버전 조회 (ID) - 존재하지 않음")
    void getVersionById_NotFound() {
        // given
        given(releaseVersionRepository.findById(anyLong())).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> releaseVersionService.getVersionById(999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RELEASE_VERSION_NOT_FOUND);
    }

    @Test
    @DisplayName("타입별 버전 목록 조회 - 성공")
    void getVersionsByType_Success() {
        // given
        ReleaseVersionDto.SimpleResponse simple = buildSimpleResponse(1L, "1.1.0");
        given(releaseVersionRepository.findAllByReleaseTypeOrderByCreatedAtDesc(anyString()))
                .willReturn(List.of(testVersion));
        given(mapper.toSimpleResponseList(any())).willReturn(new ArrayList<>(List.of(simple)));
        given(releaseFileRepository.findCategoriesByVersionId(anyLong()))
                .willReturn(Collections.emptyList());

        // when
        List<ReleaseVersionDto.SimpleResponse> result = releaseVersionService.getVersionsByType(
                "STANDARD");

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).version()).isEqualTo("1.1.0");
    }

    @Test
    @DisplayName("버전 수정 - 성공")
    void updateVersion_Success() {
        // given
        ReleaseVersionDto.UpdateRequest updateRequest = ReleaseVersionDto.UpdateRequest.builder()
                .comment("수정된 코멘트")
                .build();

        given(releaseVersionRepository.findById(anyLong())).willReturn(Optional.of(testVersion));
        given(mapper.toDetailResponse(any(ReleaseVersion.class))).willReturn(detailResponse);

        // when
        ReleaseVersionDto.DetailResponse result = releaseVersionService.updateVersion(1L,
                updateRequest);

        // then
        assertThat(result).isNotNull();
        // JPA Dirty Checking 사용 - 엔티티 조회만 검증
        then(releaseVersionRepository).should(times(1)).findById(1L);
        then(mapper).should(times(1)).toDetailResponse(any(ReleaseVersion.class));
    }

    @Test
    @DisplayName("버전 삭제 - 성공")
    void deleteVersion_Success() {
        // given
        given(releaseVersionRepository.findById(anyLong())).willReturn(Optional.of(testVersion));
        given(releaseFileRepository
                .findAllByReleaseVersion_ReleaseVersionIdOrderByExecutionOrderAsc(anyLong()))
                .willReturn(Collections.emptyList());

        // when
        releaseVersionService.deleteVersion(1L);

        // then
        then(releaseVersionRepository).should(times(1)).delete(any(ReleaseVersion.class));
        then(fileSystemService).should(times(1)).deleteVersionDirectory(any(ReleaseVersion.class));
    }

    @Test
    @DisplayName("버전 범위 조회 - 성공")
    void getVersionsBetween_Success() {
        // given
        ReleaseVersionDto.SimpleResponse simple = buildSimpleResponse(1L, "1.1.0");
        given(releaseVersionRepository.findVersionsBetween(
                anyString(), anyString(), anyString(), anyString()))
                .willReturn(List.of(testVersion));
        given(mapper.toSimpleResponseList(any())).willReturn(new ArrayList<>(List.of(simple)));
        given(releaseFileRepository.findCategoriesByVersionId(anyLong()))
                .willReturn(Collections.emptyList());

        // when
        List<ReleaseVersionDto.SimpleResponse> result = releaseVersionService.getVersionsBetween(
                PROJECT_ID, "STANDARD", "1.0.0", "1.1.0");

        // then
        assertThat(result).hasSize(1);
    }

    // === Helper Methods ===

    private ReleaseVersionDto.DetailResponse buildDetailResponse(Long id, String version,
            int major, int minor, int patch) {
        return new ReleaseVersionDto.DetailResponse(
                id,                  // releaseVersionId
                PROJECT_ID,          // projectId
                "Infraeye 2",        // projectName
                "STANDARD",          // releaseType
                null,                // customerCode
                version,             // version
                major,               // majorVersion
                minor,               // minorVersion
                patch,               // patchVersion
                0,                   // hotfixVersion
                false,               // isHotfix
                0,                   // buildVersion
                false,               // isBuild
                version,             // fullVersion
                major + "." + minor + ".x", // majorMinor
                "jhlee@tscientific", // createdByEmail
                "이재훈",             // createdByName
                null,                // createdByAvatarStyle
                null,                // createdByAvatarSeed
                false,               // isDeletedCreator
                "새로운 기능",        // comment
                false,               // isApproved
                null,                // approvedBy
                null,                // approvedByName
                false,               // isDeletedApprover
                null,                // approvedAt
                null,                // customMajorVersion
                null,                // customMinorVersion
                null,                // customPatchVersion
                null,                // customVersion
                null,                // customBaseVersionId
                null,                // customBaseVersion
                null,                // hotfixBaseVersionId
                null,                // hotfixBaseVersion
                LocalDateTime.now(), // createdAt
                new ArrayList<>()    // releaseFiles
        );
    }

    private ReleaseVersionDto.SimpleResponse buildSimpleResponse(Long id, String version) {
        return new ReleaseVersionDto.SimpleResponse(
                id,                  // releaseVersionId
                PROJECT_ID,          // projectId
                "STANDARD",          // releaseType
                null,                // customerCode
                version,             // version
                0,                   // hotfixVersion
                false,               // isHotfix
                0,                   // buildVersion
                false,               // isBuild
                version,             // fullVersion
                "1.1.x",             // majorMinor
                "jhlee@tscientific", // createdByEmail
                "이재훈",             // createdByName
                null,                // createdByAvatarStyle
                null,                // createdByAvatarSeed
                false,               // isDeletedCreator
                "새로운 기능",        // comment
                false,               // isApproved
                null,                // approvedBy
                null,                // approvedByName
                false,               // isDeletedApprover
                null,                // approvedAt
                new ArrayList<>(),   // fileCategories
                LocalDateTime.now(), // createdAt
                0                    // patchFileCount
        );
    }
}
