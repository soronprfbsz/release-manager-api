package com.ts.rm.domain.customer.entity;

import com.ts.rm.domain.project.entity.Project;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * CustomerSiteVersion Entity
 *
 * <p>사이트별 컴포넌트 현재 버전 추적 테이블.
 * <p>패치 완료(completePatch) 시점마다 upsert 되어 InfraEye {@code info version} 과
 * 동일한 상태를 유지한다.
 *
 * <ul>
 *   <li>BASE  — DB 버전 (major.minor.patch, 예: 1.1.0)</li>
 *   <li>WEB   — WAS 빌드 버전 (fullVersion, 예: 1.1.0.260511-1)</li>
 *   <li>ENGINE — 엔진 빌드 버전 (fullVersion, 예: 1.1.0.260511-1)</li>
 * </ul>
 *
 * <p>UNIQUE KEY: (customer_id, project_id, component) → row 가 없으면 INSERT, 있으면 UPDATE.
 */
@Entity
@Table(name = "customer_site_version")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerSiteVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "site_version_id")
    private Long siteVersionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    /**
     * 컴포넌트 구분: 'BASE' | 'WEB' | 'ENGINE'
     */
    @Column(name = "component", nullable = false, length = 20)
    private String component;

    /**
     * 현재 버전.
     * BASE 는 "1.1.0" 형태, WEB/ENGINE 은 fullVersion "1.1.0.260511-1" 형태.
     */
    @Column(name = "current_version", nullable = false, length = 100)
    private String currentVersion;

    /**
     * 최종 갱신 일시 (패치 완료 시점)
     */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 최종 갱신자 이메일 (패치 완료 처리자)
     */
    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    /**
     * 버전 및 갱신 정보를 업데이트한다 (upsert 시 기존 row 갱신용).
     *
     * @param version   갱신할 버전 문자열
     * @param updatedBy 갱신자 이메일
     * @param updatedAt 갱신 일시
     */
    public void updateVersion(String version, String updatedBy, LocalDateTime updatedAt) {
        this.currentVersion = version;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
    }

    /**
     * 신규 CustomerSiteVersion 생성 팩토리.
     *
     * @param customer   고객사
     * @param project    프로젝트
     * @param component  컴포넌트 구분 (BASE/WEB/ENGINE)
     * @param version    초기 버전
     * @param updatedBy  생성자 이메일
     * @param updatedAt  생성 일시
     * @return 새 CustomerSiteVersion 엔티티
     */
    public static CustomerSiteVersion create(Customer customer, Project project,
            String component, String version, String updatedBy, LocalDateTime updatedAt) {
        return CustomerSiteVersion.builder()
                .customer(customer)
                .project(project)
                .component(component)
                .currentVersion(version)
                .updatedBy(updatedBy)
                .updatedAt(updatedAt)
                .build();
    }
}
