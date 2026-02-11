package com.ts.rm.domain.releaseversion.mapper;

import com.ts.rm.domain.releasefile.mapper.ReleaseFileDtoMapper;
import com.ts.rm.domain.releaseversion.dto.ReleaseVersionDto;
import com.ts.rm.domain.releaseversion.entity.ReleaseVersion;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * ReleaseVersion Entity ↔ DTO 변환 Mapper (MapStruct로 구현)
 */
@Mapper(componentModel = "spring", uses = {ReleaseFileDtoMapper.class})
public interface ReleaseVersionDtoMapper {

    @Mapping(target = "projectId", source = "project.projectId")
    @Mapping(target = "releaseType", source = "releaseType")
    @Mapping(target = "customerCode", source = "customer.customerCode")
    @Mapping(target = "hotfixVersion", source = "hotfixVersion")
    @Mapping(target = "isHotfix", expression = "java(releaseVersion.isHotfix())")
    @Mapping(target = "fullVersion", expression = "java(releaseVersion.getFullVersion())")
    @Mapping(target = "patchFileCount", expression = "java(releaseVersion.getReleaseFiles() != null ? releaseVersion.getReleaseFiles().size() : 0)")
    @Mapping(target = "fileCategories", ignore = true)
    @Mapping(target = "createdByEmail", source = "createdByEmail")
    @Mapping(target = "createdByName", source = "createdByName")
    @Mapping(target = "createdByAvatarStyle", source = "creator.avatarStyle")
    @Mapping(target = "createdByAvatarSeed", source = "creator.avatarSeed")
    @Mapping(target = "isDeletedCreator", expression = "java(releaseVersion.getCreator() == null)")
    @Mapping(target = "approvedBy", source = "approvedByEmail")
    @Mapping(target = "approvedByName", source = "approvedByName")
    @Mapping(target = "isDeletedApprover", expression = "java(releaseVersion.getApprover() == null && releaseVersion.getApprovedByEmail() != null)")
    ReleaseVersionDto.SimpleResponse toSimpleResponse(ReleaseVersion releaseVersion);

    List<ReleaseVersionDto.SimpleResponse> toSimpleResponseList(
            List<ReleaseVersion> releaseVersions);

    @Mapping(target = "projectId", source = "project.projectId")
    @Mapping(target = "projectName", source = "project.projectName")
    @Mapping(target = "releaseType", source = "releaseType")
    @Mapping(target = "customerCode", source = "customer.customerCode")
    @Mapping(target = "hotfixVersion", source = "hotfixVersion")
    @Mapping(target = "isHotfix", expression = "java(releaseVersion.isHotfix())")
    @Mapping(target = "fullVersion", expression = "java(releaseVersion.getFullVersion())")
    @Mapping(target = "releaseFiles", source = "releaseFiles")
    @Mapping(target = "customBaseVersionId", source = "customBaseVersion.releaseVersionId")
    @Mapping(target = "customBaseVersion", source = "customBaseVersion.version")
    @Mapping(target = "hotfixBaseVersionId", source = "hotfixBaseVersion.releaseVersionId")
    @Mapping(target = "hotfixBaseVersion", source = "hotfixBaseVersion.version")
    @Mapping(target = "createdByEmail", source = "createdByEmail")
    @Mapping(target = "createdByName", source = "createdByName")
    @Mapping(target = "createdByAvatarStyle", source = "creator.avatarStyle")
    @Mapping(target = "createdByAvatarSeed", source = "creator.avatarSeed")
    @Mapping(target = "isDeletedCreator", expression = "java(releaseVersion.getCreator() == null)")
    @Mapping(target = "approvedBy", source = "approvedByEmail")
    @Mapping(target = "approvedByName", source = "approvedByName")
    @Mapping(target = "isDeletedApprover", expression = "java(releaseVersion.getApprover() == null && releaseVersion.getApprovedByEmail() != null)")
    ReleaseVersionDto.DetailResponse toDetailResponse(ReleaseVersion releaseVersion);

    List<ReleaseVersionDto.DetailResponse> toDetailResponseList(
            List<ReleaseVersion> releaseVersions);

    /**
     * 핫픽스 항목 변환
     */
    @Mapping(target = "hotfixVersion", source = "hotfixVersion")
    @Mapping(target = "fullVersion", expression = "java(releaseVersion.getFullVersion())")
    @Mapping(target = "createdAt", expression = "java(releaseVersion.getCreatedAt() != null ? releaseVersion.getCreatedAt().toString() : null)")
    @Mapping(target = "fileCategories", ignore = true)
    @Mapping(target = "createdByName", source = "createdByName")
    @Mapping(target = "createdByEmail", source = "createdByEmail")
    ReleaseVersionDto.HotfixItem toHotfixItem(ReleaseVersion releaseVersion);

    List<ReleaseVersionDto.HotfixItem> toHotfixItemList(List<ReleaseVersion> releaseVersions);
}
