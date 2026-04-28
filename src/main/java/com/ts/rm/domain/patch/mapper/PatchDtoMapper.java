package com.ts.rm.domain.patch.mapper;

import com.ts.rm.domain.patch.dto.PatchDto;
import com.ts.rm.domain.patch.entity.Patch;
import com.ts.rm.domain.patch.entity.PatchIncludedBuild;
import java.util.ArrayList;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

/**
 * Patch Entity ↔ DTO 변환 Mapper (MapStruct로 구현)
 */
@Mapper(componentModel = "spring")
public interface PatchDtoMapper {

    @Mapping(target = "projectId", source = "project.projectId")
    @Mapping(target = "releaseType", source = "releaseType")
    @Mapping(target = "customerCode", source = "customer.customerCode")
    @Mapping(target = "customerName", source = "customer.customerName")
    @Mapping(target = "createdByEmail", source = "createdByEmail")
    @Mapping(target = "createdByName", source = "createdByName")
    @Mapping(target = "createdByAvatarStyle", source = "creator.avatarStyle")
    @Mapping(target = "createdByAvatarSeed", source = "creator.avatarSeed")
    @Mapping(target = "isDeletedCreator", expression = "java(patch.getCreator() == null)")
    @Mapping(target = "assigneeId", source = "assignee.accountId")
    @Mapping(target = "assigneeName", source = "assignee.accountName")
    PatchDto.SimpleResponse toSimpleResponse(Patch patch);

    List<PatchDto.SimpleResponse> toSimpleResponseList(List<Patch> patches);

    @Mapping(target = "projectId", source = "project.projectId")
    @Mapping(target = "projectName", source = "project.projectName")
    @Mapping(target = "releaseType", source = "releaseType")
    @Mapping(target = "customerCode", source = "customer.customerCode")
    @Mapping(target = "customerName", source = "customer.customerName")
    @Mapping(target = "createdByEmail", source = "createdByEmail")
    @Mapping(target = "createdByName", source = "createdByName")
    @Mapping(target = "createdByAvatarStyle", source = "creator.avatarStyle")
    @Mapping(target = "createdByAvatarSeed", source = "creator.avatarSeed")
    @Mapping(target = "isDeletedCreator", expression = "java(patch.getCreator() == null)")
    @Mapping(target = "assigneeId", source = "assignee.accountId")
    @Mapping(target = "assigneeName", source = "assignee.accountName")
    @Mapping(target = "isBuildOnly", source = "isBuildOnly")
    @Mapping(target = "isBuildIncluded", source = "isBuildIncluded")
    @Mapping(target = "includedBuilds", source = ".", qualifiedByName = "toIncludedBuilds")
    @Mapping(target = "hotfixesInRange", source = ".", qualifiedByName = "toHotfixesInRange")
    PatchDto.DetailResponse toDetailResponse(Patch patch);

    List<PatchDto.DetailResponse> toDetailResponseList(List<Patch> patches);

    @Mapping(target = "rowNumber", ignore = true)
    @Mapping(target = "projectId", source = "project.projectId")
    @Mapping(target = "releaseType", source = "releaseType")
    @Mapping(target = "customerCode", source = "customer.customerCode")
    @Mapping(target = "customerName", source = "customer.customerName")
    @Mapping(target = "createdByEmail", source = "createdByEmail")
    @Mapping(target = "createdByName", source = "createdByName")
    @Mapping(target = "createdByAvatarStyle", source = "creator.avatarStyle")
    @Mapping(target = "createdByAvatarSeed", source = "creator.avatarSeed")
    @Mapping(target = "isDeletedCreator", expression = "java(patch.getCreator() == null)")
    @Mapping(target = "assigneeId", source = "assignee.accountId")
    @Mapping(target = "assigneeName", source = "assignee.accountName")
    @Mapping(target = "isBuildOnly", ignore = true)
    @Mapping(target = "isBuildIncluded", ignore = true)
    @Mapping(target = "includedBuildsSummary", ignore = true)
    PatchDto.ListResponse toListResponse(Patch patch);

    /**
     * Patch entity 의 includedBuilds 컬렉션을 IncludedBuilds DTO 로 변환.
     * isBuildIncluded=false 이면 빈 IncludedBuilds 를 반환.
     */
    @Named("toIncludedBuilds")
    default PatchDto.IncludedBuilds toIncludedBuilds(Patch patch) {
        if (!Boolean.TRUE.equals(patch.getIsBuildIncluded())) {
            return new PatchDto.IncludedBuilds(null, List.of());
        }
        PatchDto.IncludedWeb web = null;
        List<PatchDto.IncludedEngine> engines = new ArrayList<>();
        for (PatchIncludedBuild row : patch.getIncludedBuilds()) {
            Long bvId = row.getBuildVersion() != null ? row.getBuildVersion().getReleaseVersionId() : null;
            if ("WEB".equals(row.getKind())) {
                web = new PatchDto.IncludedWeb(bvId, row.getFullVersion());
            } else {
                engines.add(new PatchDto.IncludedEngine(row.getEngineName(), bvId, row.getFullVersion()));
            }
        }
        return new PatchDto.IncludedBuilds(web, engines);
    }

    /**
     * Patch entity 의 hotfixesInRange 컬렉션을 HotfixInRangeInfo 목록으로 변환.
     */
    @Named("toHotfixesInRange")
    default List<PatchDto.HotfixInRangeInfo> toHotfixesInRange(Patch patch) {
        return patch.getHotfixesInRange().stream()
                .map(h -> new PatchDto.HotfixInRangeInfo(
                        h.getHotfixVersion() != null ? h.getHotfixVersion().getReleaseVersionId() : null,
                        h.getFullVersion()))
                .toList();
    }
}
