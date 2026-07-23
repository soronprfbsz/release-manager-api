package com.ts.rm.domain.site.mapper;

import com.ts.rm.domain.site.dto.SiteDto;
import com.ts.rm.domain.site.entity.Site;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Site Entity ↔ DTO 변환 Mapper (MapStruct로 구현)
 */
@Mapper(componentModel = "spring")
public interface SiteDtoMapper {

    @Mapping(target = "siteId", ignore = true)
    @Mapping(target = "creator", ignore = true)
    @Mapping(target = "createdByEmail", ignore = true)
    @Mapping(target = "updater", ignore = true)
    @Mapping(target = "updatedByEmail", ignore = true)
    Site toEntity(SiteDto.CreateRequest request);

    @Mapping(target = "project", ignore = true)
    @Mapping(target = "hasCustomVersion", ignore = true)
    @Mapping(target = "createdByEmail", source = "createdByEmail")
    @Mapping(target = "createdByAvatarStyle", source = "creator.avatarStyle")
    @Mapping(target = "createdByAvatarSeed", source = "creator.avatarSeed")
    @Mapping(target = "isDeletedCreator", expression = "java(site.getCreator() == null)")
    @Mapping(target = "updatedByEmail", source = "updatedByEmail")
    @Mapping(target = "updatedByAvatarStyle", source = "updater.avatarStyle")
    @Mapping(target = "updatedByAvatarSeed", source = "updater.avatarSeed")
    @Mapping(target = "isDeletedUpdater", expression = "java(site.getUpdater() == null)")
    SiteDto.DetailResponse toDetailResponse(Site site);

    List<SiteDto.DetailResponse> toDetailResponseList(List<Site> sites);

    SiteDto.SimpleResponse toSimpleResponse(Site site);

    List<SiteDto.SimpleResponse> toSimpleResponseList(List<Site> sites);

    @Mapping(target = "rowNumber", ignore = true)
    @Mapping(target = "project", ignore = true)
    @Mapping(target = "hasCustomVersion", ignore = true)
    SiteDto.ListResponse toListResponse(Site site);
}
