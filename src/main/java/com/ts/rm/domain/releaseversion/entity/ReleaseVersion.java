package com.ts.rm.domain.releaseversion.entity;

import com.ts.rm.domain.account.entity.Account;
import com.ts.rm.domain.customer.entity.Customer;
import com.ts.rm.domain.project.entity.Project;
import com.ts.rm.domain.releasefile.entity.ReleaseFile;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "release_version")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReleaseVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "release_version_id")
    private Long releaseVersionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "release_type", nullable = false, length = 20)
    private String releaseType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(nullable = false, length = 50)
    private String version;

    @Column(name = "major_version", nullable = false)
    private Integer majorVersion;

    @Column(name = "minor_version", nullable = false)
    private Integer minorVersion;

    @Column(name = "patch_version", nullable = false)
    private Integer patchVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private Account creator;

    @Column(name = "created_by_email", length = 100)
    private String createdByEmail;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "is_approved", nullable = false)
    @Builder.Default
    private Boolean isApproved = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private Account approver;

    @Column(name = "approved_by_email", length = 100)
    private String approvedByEmail;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "hotfix_version", nullable = false)
    @Builder.Default
    private Integer hotfixVersion = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hotfix_base_version_id")
    private ReleaseVersion hotfixBaseVersion;

    @Column(name = "custom_major_version")
    private Integer customMajorVersion;

    @Column(name = "custom_minor_version")
    private Integer customMinorVersion;

    @Column(name = "custom_patch_version")
    private Integer customPatchVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "custom_base_version_id")
    private ReleaseVersion customBaseVersion;

    @OneToMany(mappedBy = "releaseVersion", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ReleaseFile> releaseFiles = new ArrayList<>();

    /**
     * 엔티티 저장 전 createdAt을 UTC로 설정
     */
    @PrePersist
    protected void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
        }
    }

    /**
     * 릴리즈 파일 추가
     */
    public void addReleaseFile(ReleaseFile releaseFile) {
        this.releaseFiles.add(releaseFile);
        releaseFile.setReleaseVersion(this);
    }

    /**
     * 버전 키 생성 (예: standard/1.1.0 또는 custom/company_a/1.0.0)
     */
    public String getVersionKey() {
        StringBuilder key = new StringBuilder();
        key.append(releaseType.toLowerCase()).append("/");
        if (customer != null) {
            key.append(customer.getCustomerCode()).append("/");
        }
        key.append(version);
        return key.toString();
    }

    /**
     * Major.Minor 버전 계산 (예: 1.1.x)
     *
     * <p>DB 컬럼 없이 majorVersion, minorVersion으로 동적 계산
     */
    @Transient
    public String getMajorMinor() {
        return majorVersion + "." + minorVersion + ".x";
    }

    /**
     * 커스텀 버전 문자열 반환 (예: 1.0.0)
     *
     * <p>customMajorVersion, customMinorVersion, customPatchVersion이 모두 존재할 때만 반환
     */
    @Transient
    public String getCustomVersion() {
        if (customMajorVersion == null || customMinorVersion == null || customPatchVersion == null) {
            return null;
        }
        return customMajorVersion + "." + customMinorVersion + "." + customPatchVersion;
    }

    /**
     * 커스텀 버전 존재 여부 확인
     */
    @Transient
    public boolean hasCustomVersion() {
        return customMajorVersion != null && customMinorVersion != null && customPatchVersion != null;
    }

    /**
     * 커스텀 버전의 Major.Minor 계산 (예: 1.0.x)
     */
    @Transient
    public String getCustomMajorMinor() {
        if (customMajorVersion == null || customMinorVersion == null) {
            return null;
        }
        return customMajorVersion + "." + customMinorVersion + ".x";
    }

    /**
     * 커스텀 릴리즈 여부 확인
     */
    @Transient
    public boolean isCustomRelease() {
        return "CUSTOM".equals(releaseType);
    }

    /**
     * 핫픽스 여부 확인
     */
    @Transient
    public boolean isHotfix() {
        return hotfixVersion != null && hotfixVersion > 0;
    }

    /**
     * 전체 버전 문자열 반환 (핫픽스 포함)
     * <p>표준 버전: 1.3.2, 핫픽스: 1.3.2.1
     * <p>커스텀 버전: 1.1.0-customerA.1.0.0, 핫픽스: 1.1.0-customerA.1.0.0.1
     */
    @Transient
    public String getFullVersion() {
        // 커스텀 버전인 경우 version 필드 기반으로 반환
        if ("CUSTOM".equals(releaseType) || customMajorVersion != null) {
            if (isHotfix()) {
                return version + "." + hotfixVersion;
            }
            return version;
        }
        // 표준 버전
        if (isHotfix()) {
            return majorVersion + "." + minorVersion + "." + patchVersion + "." + hotfixVersion;
        }
        return majorVersion + "." + minorVersion + "." + patchVersion;
    }

    /**
     * 기본 버전 문자열 반환 (핫픽스 제외)
     * <p>1.3.2.1 → 1.3.2
     */
    @Transient
    public String getBaseVersionString() {
        return majorVersion + "." + minorVersion + "." + patchVersion;
    }

    /**
     * 생성자 이름 반환
     * <p>creator 엔티티의 accountName 반환, null이면 null 반환
     */
    @Transient
    public String getCreatedByName() {
        return creator != null ? creator.getAccountName() : null;
    }

    /**
     * 생성자 아바타 스타일 반환
     * <p>creator 엔티티의 avatarStyle 반환, null이면 null 반환
     */
    @Transient
    public String getCreatedByAvatarStyle() {
        return creator != null ? creator.getAvatarStyle() : null;
    }

    /**
     * 생성자 아바타 시드 반환
     * <p>creator 엔티티의 avatarSeed 반환, null이면 null 반환
     */
    @Transient
    public String getCreatedByAvatarSeed() {
        return creator != null ? creator.getAvatarSeed() : null;
    }

    /**
     * 승인자 이름 반환
     * <p>approver 엔티티의 accountName 반환, null이면 null 반환
     */
    @Transient
    public String getApprovedByName() {
        return approver != null ? approver.getAccountName() : null;
    }

    /**
     * 승인자 아바타 스타일 반환
     * <p>approver 엔티티의 avatarStyle 반환, null이면 null 반환
     */
    @Transient
    public String getApprovedByAvatarStyle() {
        return approver != null ? approver.getAvatarStyle() : null;
    }

    /**
     * 승인자 아바타 시드 반환
     * <p>approver 엔티티의 avatarSeed 반환, null이면 null 반환
     */
    @Transient
    public String getApprovedByAvatarSeed() {
        return approver != null ? approver.getAvatarSeed() : null;
    }
}
