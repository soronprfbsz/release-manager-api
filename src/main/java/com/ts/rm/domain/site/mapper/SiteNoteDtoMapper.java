package com.ts.rm.domain.site.mapper;

import com.ts.rm.domain.site.dto.SiteNoteDto;
import com.ts.rm.domain.site.entity.SiteNote;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * SiteNote Entity ↔ DTO 변환 Mapper (MapStruct로 구현)
 */
@Mapper(componentModel = "spring")
public interface SiteNoteDtoMapper {

    @Mapping(target = "noteId", ignore = true)
    @Mapping(target = "site", ignore = true)
    @Mapping(target = "creator", ignore = true)
    @Mapping(target = "createdByEmail", ignore = true)
    @Mapping(target = "updater", ignore = true)
    @Mapping(target = "updatedByEmail", ignore = true)
    SiteNote toEntity(SiteNoteDto.CreateRequest request);

    @Mapping(target = "siteId", source = "site.siteId")
    @Mapping(target = "createdByEmail", source = "createdByEmail")
    @Mapping(target = "createdByName", source = "creator.accountName")
    @Mapping(target = "createdByAvatarStyle", source = "creator.avatarStyle")
    @Mapping(target = "createdByAvatarSeed", source = "creator.avatarSeed")
    @Mapping(target = "isDeletedCreator", expression = "java(note.getCreator() == null)")
    @Mapping(target = "updatedByEmail", source = "updatedByEmail")
    @Mapping(target = "updatedByName", source = "updater.accountName")
    @Mapping(target = "updatedByAvatarStyle", source = "updater.avatarStyle")
    @Mapping(target = "updatedByAvatarSeed", source = "updater.avatarSeed")
    @Mapping(target = "isDeletedUpdater", expression = "java(note.getUpdater() == null)")
    SiteNoteDto.Response toResponse(SiteNote note);

    List<SiteNoteDto.Response> toResponseList(List<SiteNote> notes);
}
