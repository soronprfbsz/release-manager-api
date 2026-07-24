package com.ts.rm.domain.site.entity;

import com.ts.rm.domain.account.entity.Account;
import com.ts.rm.domain.common.entity.BaseEntity;
import com.ts.rm.domain.site.enums.SiteCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "customer")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Site extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "customer_id")
    private Long siteId;

    @Column(name = "customer_code", nullable = false, unique = true, length = 50)
    private String siteCode;

    @Column(name = "customer_name", nullable = false, length = 100)
    private String siteName;

    /**
     * 사이트 구분 (고객사 / 내부 테스트). 좌측 트리 그룹핑 기준.
     * DB 컬럼은 site_category (customer 테이블 유지 정책상 테이블명만 customer).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "site_category", nullable = false, length = 20)
    private SiteCategory siteCategory;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    /**
     * 카드 좌상단 글리프 텍스트 (1~3자)
     */
    @Column(name = "glyph_text", length = 3)
    private String glyphText;

    /**
     * 글리프 배경 색상 키 (예: mint, lavender, peach)
     */
    @Column(name = "glyph_background_color", length = 30)
    private String glyphBackgroundColor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private Account creator;

    /**
     * 생성자 이메일 (계정 삭제 시에도 유지)
     */
    @Column(name = "created_by_email", length = 100)
    private String createdByEmail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private Account updater;

    /**
     * 수정자 이메일 (계정 삭제 시에도 유지)
     */
    @Column(name = "updated_by_email", length = 100)
    private String updatedByEmail;

    /**
     * 사이트 구분 기본값 보정 — 명시되지 않으면 고객사(CUSTOMER).
     * 생성 요청은 DTO 에서 이미 기본값을 채우지만, 모든 영속화 경로에서
     * NOT NULL 을 보장하기 위한 방어 훅.
     */
    @PrePersist
    private void applyDefaultCategory() {
        if (siteCategory == null) {
            siteCategory = SiteCategory.CUSTOMER;
        }
    }

    /**
     * 생성자 이름 반환 헬퍼 메서드
     */
    @Transient
    public String getCreatedByName() {
        return creator != null ? creator.getAccountName() : null;
    }

    /**
     * 수정자 이름 반환 헬퍼 메서드
     */
    @Transient
    public String getUpdatedByName() {
        return updater != null ? updater.getAccountName() : null;
    }

    /**
     * 글리프 정보 수정
     * null 이면 기존 값 유지, 빈 문자열("")이면 null 로 저장 (글리프 제거 의도).
     */
    public void updateGlyph(String glyphText, String glyphBackgroundColor) {
        if (glyphText != null) {
            this.glyphText = glyphText.isBlank() ? null : glyphText;
        }
        if (glyphBackgroundColor != null) {
            this.glyphBackgroundColor = glyphBackgroundColor.isBlank() ? null : glyphBackgroundColor;
        }
    }
}
