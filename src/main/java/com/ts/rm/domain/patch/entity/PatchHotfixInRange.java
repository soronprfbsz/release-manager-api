package com.ts.rm.domain.patch.entity;

import com.ts.rm.domain.common.entity.BaseEntity;
import com.ts.rm.domain.releaseversion.entity.ReleaseVersion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 패치 범위 안에 존재한 핫픽스 메타 행. 별도 적용 안내용.
 *
 * <p>spec §3.2. created_at / updated_at 은 BaseEntity 가 관리.
 */
@Entity
@Table(name = "patch_hotfix_in_range")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatchHotfixInRange extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "patch_hotfix_in_range_id")
    private Long patchHotfixInRangeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patch_id", nullable = false)
    private Patch patch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hotfix_version_id")
    private ReleaseVersion hotfixVersion;

    @Column(name = "full_version", nullable = false, length = 50)
    private String fullVersion;

    @Column(name = "hotfix_version", nullable = false)
    private Integer hotfixVersionNumber;
}
