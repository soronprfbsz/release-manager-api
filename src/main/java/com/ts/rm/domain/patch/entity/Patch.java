package com.ts.rm.domain.patch.entity;

import com.ts.rm.domain.account.entity.Account;
import com.ts.rm.domain.customer.entity.Customer;
import com.ts.rm.domain.project.entity.Project;
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
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Patch Entity
 *
 * <p>패치 파일 테이블 - 패치 생성 기록 관리
 */
@Entity
@Table(name = "patch_file")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Patch extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "patch_id")
    private Long patchId;

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

    @Column(name = "output_path", nullable = false, length = 500)
    private String outputPath;

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

    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * 패치 담당자
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private Account assignee;

    @Column(name = "is_build_only", nullable = false)
    @Builder.Default
    private Boolean isBuildOnly = false;

    @Column(name = "is_build_included", nullable = false)
    @Builder.Default
    private Boolean isBuildIncluded = false;

    @OneToMany(
            mappedBy = "patch",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @Builder.Default
    private List<PatchIncludedBuild> includedBuilds = new ArrayList<>();

    @OneToMany(
            mappedBy = "patch",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @Builder.Default
    private List<PatchHotfixInRange> hotfixesInRange = new ArrayList<>();

    public void addIncludedBuild(PatchIncludedBuild item) {
        item.setPatch(this);
        this.includedBuilds.add(item);
    }

    public void addHotfixInRange(PatchHotfixInRange item) {
        item.setPatch(this);
        this.hotfixesInRange.add(item);
    }

    /**
     * 생성자 이름 반환 헬퍼 메서드
     */
    @Transient
    public String getCreatedByName() {
        return creator != null ? creator.getAccountName() : null;
    }
}
