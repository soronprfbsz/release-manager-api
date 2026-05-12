package com.ts.rm.domain.publishing.entity;

import com.ts.rm.domain.account.entity.Account;
import com.ts.rm.domain.common.entity.BaseEntity;
import com.ts.rm.domain.customer.entity.Customer;
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
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Publishing Entity
 *
 * <p>퍼블리싱(HTML, CSS, JS 등 웹 화면단 리소스) 관리 엔티티
 * ZIP 파일로 업로드하여 폴더 구조를 유지
 */
@Entity
@Table(name = "publishing")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Publishing extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "publishing_id")
    private Long publishingId;

    /**
     * 퍼블리싱 명
     */
    @Column(name = "publishing_name", nullable = false, length = 255)
    private String publishingName;

    /**
     * 퍼블리싱 설명
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * 카테고리 (code_type: PUBLISHING_CATEGORY)
     * 예: INFRAEYE1, INFRAEYE2, COMMON, ETC
     */
    @Column(name = "publishing_category", nullable = false, length = 50)
    private String publishingCategory;

    /**
     * 서브 카테고리 (code_type: PUBLISHING_SUBCATEGORY_XXX)
     * 예: DASHBOARD, REPORT, MONITORING, ETC
     */
    @Column(name = "sub_category", length = 50)
    private String subCategory;

    /**
     * 고객사 (커스터마이징 퍼블리싱인 경우)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    /**
     * 정렬 순서
     */
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
     * 퍼블리싱 파일 목록
     */
    @OneToMany(mappedBy = "publishing", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PublishingFile> files = new ArrayList<>();

    /**
     * 퍼블리싱 파일 추가
     *
     * @param file 추가할 퍼블리싱 파일
     */
    public void addFile(PublishingFile file) {
        this.files.add(file);
        file.setPublishing(this);
    }

    /**
     * 퍼블리싱 파일 제거
     *
     * @param file 제거할 퍼블리싱 파일
     */
    public void removeFile(PublishingFile file) {
        this.files.remove(file);
        file.setPublishing(null);
    }

    /**
     * 퍼블리싱 정보 수정
     *
     * <p>null 이면 기존 값 유지, 빈 문자열("")이면 null 로 저장 (글리프 제거 의도).
     *
     * @param glyphText            글리프 텍스트 (null=미변경, ""=제거)
     * @param glyphBackgroundColor 글리프 배경 색상 키 (null=미변경, ""=제거)
     */
    public void updateGlyph(String glyphText, String glyphBackgroundColor) {
        if (glyphText != null) {
            this.glyphText = glyphText.isBlank() ? null : glyphText;
        }
        if (glyphBackgroundColor != null) {
            this.glyphBackgroundColor = glyphBackgroundColor.isBlank() ? null : glyphBackgroundColor;
        }
    }

    /**
     * 모든 퍼블리싱 파일 제거
     */
    public void clearFiles() {
        this.files.forEach(file -> file.setPublishing(null));
        this.files.clear();
    }
}
