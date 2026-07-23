package com.ts.rm.domain.publishing.mapper;

import com.ts.rm.domain.publishing.dto.PublishingDto;
import com.ts.rm.domain.publishing.dto.PublishingFileDto;
import com.ts.rm.domain.publishing.entity.Publishing;
import com.ts.rm.domain.publishing.entity.PublishingFile;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Publishing DTO Mapper
 */
@Mapper(componentModel = "spring")
public interface PublishingDtoMapper {

    /**
     * Entity -> SimpleResponse 변환
     * 주의: htmlFiles는 PublishingService에서 직접 처리 (커스텀 URL 생성 로직 필요)
     */
    @Mapping(target = "siteName", source = "site.siteName")
    @Mapping(target = "fileCount", expression = "java(publishing.getFiles() != null ? publishing.getFiles().size() : 0)")
    @Mapping(target = "htmlFiles", ignore = true)
    PublishingDto.SimpleResponse toSimpleResponse(Publishing publishing);

    /**
     * Entity List -> SimpleResponse List 변환
     */
    List<PublishingDto.SimpleResponse> toSimpleResponseList(List<Publishing> publishings);

    /**
     * PublishingFile Entity -> SimpleResponse 변환
     */
    PublishingFileDto.SimpleResponse toFileSimpleResponse(PublishingFile publishingFile);

    /**
     * PublishingFile Entity List -> SimpleResponse List 변환
     */
    List<PublishingFileDto.SimpleResponse> toFileSimpleResponseList(List<PublishingFile> publishingFiles);

    /**
     * PublishingFile Entity -> DetailResponse 변환
     */
    @Mapping(target = "publishingId", source = "publishing.publishingId")
    PublishingFileDto.DetailResponse toFileDetailResponse(PublishingFile publishingFile);
}
