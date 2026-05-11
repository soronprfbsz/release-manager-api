package com.ts.rm.domain.customer.service;

import com.ts.rm.domain.customer.dto.CustomerSiteVersionDto;
import com.ts.rm.domain.customer.entity.Customer;
import com.ts.rm.domain.customer.entity.CustomerSiteVersion;
import com.ts.rm.domain.customer.repository.CustomerRepository;
import com.ts.rm.domain.customer.repository.CustomerSiteVersionRepository;
import com.ts.rm.domain.project.entity.Project;
import com.ts.rm.domain.project.repository.ProjectRepository;
import com.ts.rm.global.exception.BusinessException;
import com.ts.rm.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사이트별 컴포넌트 버전 추적 서비스
 *
 * <p>패치 완료(completePatch) 시 BASE/WEB/ENGINE 컴포넌트 별 현재 버전을 upsert 하여
 * InfraEye {@code info version} 과 동일한 버전 상태를 유지한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerSiteVersionService {

    private final CustomerSiteVersionRepository siteVersionRepository;
    private final CustomerRepository customerRepository;
    private final ProjectRepository projectRepository;

    /**
     * 사이트 컴포넌트 버전 upsert.
     *
     * <p>UNIQUE KEY (customer_id, project_id, component) 기준으로
     * row 가 없으면 INSERT, 있으면 currentVersion / updatedBy / updatedAt UPDATE.
     *
     * @param customerId 고객사 ID
     * @param projectId  프로젝트 ID
     * @param component  컴포넌트 구분 (BASE/WEB/ENGINE)
     * @param version    갱신할 버전 문자열
     * @param updatedBy  갱신자 이메일
     * @param updatedAt  갱신 일시
     */
    @Transactional
    public void upsert(Long customerId, String projectId, String component,
            String version, String updatedBy, LocalDateTime updatedAt) {

        siteVersionRepository
                .findByCustomer_CustomerIdAndProject_ProjectIdAndComponent(
                        customerId, projectId, component)
                .ifPresentOrElse(
                        existing -> {
                            // 기존 row 업데이트
                            existing.updateVersion(version, updatedBy, updatedAt);
                            siteVersionRepository.save(existing);
                            log.info("사이트 버전 갱신 - customerId: {}, projectId: {}, component: {}, version: {}",
                                    customerId, projectId, component, version);
                        },
                        () -> {
                            // 신규 row 생성
                            Customer customer = customerRepository.findById(customerId)
                                    .orElseThrow(() -> new BusinessException(
                                            ErrorCode.CUSTOMER_NOT_FOUND,
                                            "고객사를 찾을 수 없습니다: " + customerId));
                            Project project = projectRepository.findById(projectId)
                                    .orElseThrow(() -> new BusinessException(
                                            ErrorCode.PROJECT_NOT_FOUND,
                                            "프로젝트를 찾을 수 없습니다: " + projectId));
                            CustomerSiteVersion newEntry = CustomerSiteVersion.create(
                                    customer, project, component, version, updatedBy, updatedAt);
                            siteVersionRepository.save(newEntry);
                            log.info("사이트 버전 신규 등록 - customerId: {}, projectId: {}, component: {}, version: {}",
                                    customerId, projectId, component, version);
                        }
                );
    }

    /**
     * 고객사 + 프로젝트의 컴포넌트 버전 목록 조회 (UI 표시용).
     *
     * @param customerId 고객사 ID
     * @param projectId  프로젝트 ID
     * @return 컴포넌트 버전 목록 (최대 3건: BASE/WEB/ENGINE)
     */
    public List<CustomerSiteVersionDto.SiteVersionResponse> findByCustomerAndProject(
            Long customerId, String projectId) {

        List<CustomerSiteVersion> rows =
                siteVersionRepository.findAllByCustomer_CustomerIdAndProject_ProjectIdOrderByComponent(
                        customerId, projectId);

        return rows.stream()
                .map(r -> new CustomerSiteVersionDto.SiteVersionResponse(
                        r.getComponent(),
                        r.getCurrentVersion(),
                        r.getUpdatedAt(),
                        r.getUpdatedBy()))
                .toList();
    }
}
