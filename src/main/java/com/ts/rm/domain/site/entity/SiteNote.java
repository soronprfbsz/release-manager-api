package com.ts.rm.domain.site.entity;

import com.ts.rm.domain.account.entity.Account;
import com.ts.rm.domain.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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
 * SiteNote Entity
 *
 * <p>사이트 특이사항 테이블 - 사이트별 메모/특이사항 관리
 */
@Entity
@Table(name = "customer_note")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SiteNote extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "note_id")
    private Long noteId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Site site;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private Account creator;

    @Column(name = "created_by_email", length = 100)
    private String createdByEmail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private Account updater;

    @Column(name = "updated_by_email", length = 100)
    private String updatedByEmail;

    /**
     * 작성자 이름 반환 헬퍼 메서드
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
}
