package com.ts.rm.domain.patch.entity;

import com.ts.rm.domain.account.entity.Account;
import com.ts.rm.domain.customer.entity.Customer;
import com.ts.rm.domain.project.entity.Project;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * PatchHistory Entity
 *
 * <p>패치 이력 테이블 - patch_file 삭제와 무관하게 패치 이력 영구 보존
 * <p>삭제 없이 append only로 운영
 */
@Entity
@Table(name = "patch_history")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatchHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long historyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "release_type", nullable = false, length = 20)
    private String releaseType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(name = "from_version", nullable = false, length = 50)
    private String fromVersion;

    @Column(name = "to_version", nullable = false, length = 50)
    private String toVersion;

    @Column(name = "patch_name", nullable = false, length = 100)
    private String patchName;

    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * 패치 담당자
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private Account assignee;

    /**
     * 담당자 이메일 (계정 삭제 시에도 유지)
     */
    @Column(name = "assignee_email", length = 100)
    private String assigneeEmail;

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

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 패치 완료 일시
     */
    @Column(name = "completed_at", nullable = false)
    private LocalDateTime completedAt;

    /**
     * 패치 완료 처리자 이메일 (완료 시점 로그인 사용자)
     */
    @Column(name = "completed_by", length = 255)
    private String completedBy;

    /**
     * Patch 엔티티로부터 PatchHistory 생성 (패치 완료 시점 호출)
     *
     * @param patch       완료 처리할 Patch 엔티티
     * @param completedBy 완료 처리자 이메일
     * @param completedAt 완료 일시
     */
    public static PatchHistory fromPatch(Patch patch, String completedBy, LocalDateTime completedAt) {
        return PatchHistory.builder()
                .project(patch.getProject())
                .releaseType(patch.getReleaseType())
                .customer(patch.getCustomer())
                .fromVersion(patch.getFromVersion())
                .toVersion(patch.getToVersion())
                .patchName(patch.getPatchName())
                .description(patch.getDescription())
                .assignee(patch.getAssignee())
                .assigneeEmail(patch.getAssignee() != null ? patch.getAssignee().getEmail() : null)
                .creator(patch.getCreator())
                .createdByEmail(patch.getCreatedByEmail())
                .completedBy(completedBy)
                .completedAt(completedAt)
                .build();
    }

    /**
     * 생성자 이름 반환 헬퍼 메서드
     */
    @Transient
    public String getCreatedByName() {
        return creator != null ? creator.getAccountName() : null;
    }

    /**
     * 담당자 이름 반환 헬퍼 메서드
     */
    @Transient
    public String getAssigneeName() {
        return assignee != null ? assignee.getAccountName() : null;
    }
}
