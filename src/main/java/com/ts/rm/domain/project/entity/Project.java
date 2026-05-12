package com.ts.rm.domain.project.entity;

import com.ts.rm.domain.account.entity.Account;
import com.ts.rm.domain.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Project Entity
 *
 * <p>프로젝트(제품) 정보 관리
 * <p>각 프로젝트별로 릴리즈 버전과 패치를 분리하여 관리
 */
@Entity
@Table(name = "project")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Project extends BaseEntity {

    @Id
    @Column(name = "project_id", length = 50)
    private String projectId;

    @Column(name = "project_name", nullable = false, length = 100)
    private String projectName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_enabled", nullable = false)
    @Builder.Default
    private Boolean isEnabled = true;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private Account creator;

    /**
     * 생성자 이메일 (계정 삭제 시에도 유지)
     */
    @Column(name = "created_by_email", length = 100)
    private String createdByEmail;

    /**
     * 생성자 이름 반환 헬퍼 메서드
     */
    @Transient
    public String getCreatedByName() {
        return creator != null ? creator.getAccountName() : null;
    }

    /**
     * 프로젝트 정보 수정
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
}
