package com.ts.rm.domain.releasefile.entity;

import com.ts.rm.domain.releasefile.enums.FileCategory;
import com.ts.rm.domain.releasefile.util.SubCategoryValidator;
import com.ts.rm.domain.releaseversion.entity.ReleaseVersion;
import com.ts.rm.domain.common.entity.BaseEntity;
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
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ReleaseFile Entity
 */
@Entity
@Table(name = "release_file")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReleaseFile extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "release_file_id")
    private Long releaseFileId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "release_version_id", nullable = false)
    private ReleaseVersion releaseVersion;

    @Column(name = "file_type", nullable = false, length = 50)
    private String fileType;

    /**
     * 파일 카테고리 (DATABASE, WEB, ENGINE, ETC)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "file_category", length = 50)
    private FileCategory fileCategory;

    /**
     * 하위 카테고리 (MARIADB, CRATEDB, BUILD, SH, IMAGE, METADATA, ETC 등)
     */
    @Column(name = "sub_category", length = 50)
    private String subCategory;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(length = 64)
    private String checksum;

    @Column(name = "execution_order", nullable = false)
    private Integer executionOrder;

    @Column(length = 500)
    private String description;

    /**
     * filePath에서 상대 경로 추출 (Transient 계산 필드)
     *
     * <p>filePath 구조:
     * <ul>
     *   <li>표준: versions/{projectId}/standard/{majorMinor}/{version}/{relativePath}
     *   <li>커스텀: versions/{projectId}/custom/{siteCode}/{majorMinor}/{version}/{relativePath}
     * </ul>
     *
     * <p>예시:
     * <ul>
     *   <li>filePath: versions/infraeye2/standard/1.0.x/1.0.0/install/file.sql
     *   <li>반환값: install/file.sql
     * </ul>
     *
     * @return 상대 경로 (선행 슬래시 없음)
     */
    @Transient
    public String getRelativePath() {
        if (filePath == null || filePath.isEmpty()) {
            return fileName;
        }

        String[] parts = filePath.split("/");
        if (parts.length < 6) {
            return fileName;
        }

        // parts[0] = "versions"
        // parts[1] = projectId
        // parts[2] = type (standard, custom)
        // 표준: parts[3] = majorMinor, parts[4] = version, parts[5...] = relativePath
        // 커스텀: parts[3] = siteCode, parts[4] = majorMinor, parts[5] = version, parts[6...] = relativePath

        int relativeStartIndex;
        if ("versions".equals(parts[0])) {
            if (parts.length > 2 && "custom".equals(parts[2])) {
                // 커스텀: 6번째 이후가 상대 경로
                relativeStartIndex = 6;
            } else {
                // 표준: 5번째 이후가 상대 경로
                relativeStartIndex = 5;
            }

            if (relativeStartIndex >= parts.length) {
                return fileName;
            }

            StringBuilder relativePath = new StringBuilder();
            for (int i = relativeStartIndex; i < parts.length; i++) {
                if (i > relativeStartIndex) {
                    relativePath.append("/");
                }
                relativePath.append(parts[i]);
            }
            return relativePath.toString();
        }

        return fileName;
    }
}
