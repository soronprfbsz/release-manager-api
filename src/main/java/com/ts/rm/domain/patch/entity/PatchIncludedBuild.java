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
 * 패치에 포함된 빌드 메타 행. WEB 1개 또는 ENGINE N개로 구성된다.
 *
 * <p>spec §3.1. 빌드 row 가 후일 삭제되면 build_version_id 는 NULL 로 setting (ON DELETE SET NULL),
 * full_version snapshot 만 보존된다. created_at / updated_at 은 BaseEntity 가 관리.
 */
@Entity
@Table(name = "patch_included_build")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatchIncludedBuild extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "patch_included_build_id")
    private Long patchIncludedBuildId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patch_id", nullable = false)
    private Patch patch;

    /** 'WEB' | 'ENGINE'. spec §3.1 의미 명세. */
    @Column(name = "kind", nullable = false, length = 10)
    private String kind;

    /** kind='ENGINE' 일 때만 채워진다. WEB 은 NULL. */
    @Column(name = "engine_name", length = 50)
    private String engineName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "build_version_id")
    private ReleaseVersion buildVersion;

    @Column(name = "full_version", nullable = false, length = 50)
    private String fullVersion;
}
