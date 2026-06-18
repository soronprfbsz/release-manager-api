package com.ts.rm.domain.patch.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * PatchHistoryBuild Entity
 *
 * <p>패치 이력별 WEB/ENGINE 빌드 스냅샷. patch_included_build 의 미러로,
 * 패치 완료 시점에 복사 보존되어 이력 삭제 후 버전 재계산의 근거가 된다.
 */
@Entity
@Table(name = "patch_history_build")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatchHistoryBuild {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "patch_history_build_id")
    private Long patchHistoryBuildId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "history_id", nullable = false)
    private PatchHistory history;

    /** 'WEB' | 'ENGINE' */
    @Column(name = "kind", nullable = false, length = 10)
    private String kind;

    /** kind='ENGINE' 일 때만 채움. WEB 은 NULL. */
    @Column(name = "engine_name", length = 50)
    private String engineName;

    @Column(name = "full_version", nullable = false, length = 50)
    private String fullVersion;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * 빌드 스냅샷 생성 팩토리 (패치 완료 시점 호출).
     */
    public static PatchHistoryBuild of(PatchHistory history, String kind, String engineName,
            String fullVersion, LocalDateTime createdAt) {
        return PatchHistoryBuild.builder()
                .history(history)
                .kind(kind)
                .engineName(engineName)
                .fullVersion(fullVersion)
                .createdAt(createdAt)
                .build();
    }
}
