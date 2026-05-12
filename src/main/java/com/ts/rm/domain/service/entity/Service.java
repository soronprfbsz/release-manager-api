package com.ts.rm.domain.service.entity;

import com.ts.rm.domain.account.entity.Account;
import com.ts.rm.domain.common.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Service Entity
 *
 * <p>서비스 관리 엔티티 - 복합 서비스의 기본 정보를 관리합니다.
 */
@Entity
@Table(name = "service")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Service extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "service_id")
    private Long serviceId;

    @Column(name = "service_name", nullable = false, length = 255)
    private String serviceName;

    @Column(name = "service_type", nullable = false, length = 50)
    private String serviceType;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    /**
     * 카드 좌상단 글리프 텍스트 (1~3자, nullable)
     */
    @Column(name = "glyph_text", length = 3)
    private String glyphText;

    /**
     * 글리프 배경 색상 키 (nullable)
     */
    @Column(name = "glyph_background_color", length = 30)
    private String glyphBackgroundColor;

    /**
     * 생성자
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private Account creator;

    /**
     * 생성자 이메일 (계정 삭제 시에도 유지)
     */
    @Column(name = "created_by_email", length = 100)
    private String createdByEmail;

    /**
     * 수정자
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private Account updater;

    /**
     * 수정자 이메일 (계정 삭제 시에도 유지)
     */
    @Column(name = "updated_by_email", length = 100)
    private String updatedByEmail;

    @OneToMany(mappedBy = "service", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    @Builder.Default
    private List<ServiceComponent> components = new ArrayList<>();

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
     * 서비스 정보 수정
     *
     * <p>null 이면 기존 값 유지, 빈 문자열("")이면 null 로 저장 (글리프 제거 의도).
     *
     * @param serviceName          서비스명
     * @param serviceType          서비스 분류
     * @param description          설명
     * @param updater              수정자
     * @param glyphText            글리프 텍스트 (null=미변경, ""=제거)
     * @param glyphBackgroundColor 글리프 배경 색상 키 (null=미변경, ""=제거)
     */
    public void update(String serviceName, String serviceType,
                       String description, Account updater,
                       String glyphText, String glyphBackgroundColor) {
        if (serviceName != null && !serviceName.isBlank()) {
            this.serviceName = serviceName;
        }
        if (serviceType != null && !serviceType.isBlank()) {
            this.serviceType = serviceType;
        }
        if (description != null) {
            this.description = description;
        }
        if (updater != null) {
            this.updater = updater;
        }
        if (glyphText != null) {
            this.glyphText = glyphText.isBlank() ? null : glyphText;
        }
        if (glyphBackgroundColor != null) {
            this.glyphBackgroundColor = glyphBackgroundColor.isBlank() ? null : glyphBackgroundColor;
        }
    }

    /**
     * 컴포넌트 추가
     *
     * @param component 서비스 컴포넌트
     */
    public void addComponent(ServiceComponent component) {
        this.components.add(component);
        component.setService(this);
    }

    /**
     * 컴포넌트 제거
     *
     * @param component 서비스 컴포넌트
     */
    public void removeComponent(ServiceComponent component) {
        this.components.remove(component);
        component.setService(null);
    }

    /**
     * 모든 컴포넌트 제거
     */
    public void removeAllComponents() {
        this.components.clear();
    }

    /**
     * creator 설정 (생성 시 사용)
     *
     * @param creator 생성자
     */
    public void setCreator(Account creator) {
        this.creator = creator;
    }

    /**
     * sortOrder 설정
     *
     * @param sortOrder 정렬 순서
     */
    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    /**
     * 수정자 이메일 설정
     *
     * @param updatedByEmail 수정자 이메일
     */
    public void setUpdatedByEmail(String updatedByEmail) {
        this.updatedByEmail = updatedByEmail;
    }
}
