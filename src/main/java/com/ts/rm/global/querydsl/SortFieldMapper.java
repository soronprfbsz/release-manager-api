package com.ts.rm.global.querydsl;

import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * API 정렬 필드를 엔티티 경로로 매핑하는 유틸리티
 *
 * <p>프론트엔드는 간단한 필드명(예: siteName)으로 정렬을 요청하고,
 * <p>백엔드는 실제 엔티티 경로(예: site.siteName)로 변환하여 처리
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SortFieldMapper {

    /**
     * Pageable의 정렬 필드를 매핑하여 새로운 Pageable 반환
     *
     * @param pageable 원본 Pageable
     * @param fieldMap 필드 매핑 (API 필드명 -> 엔티티 경로)
     * @return 매핑된 정렬이 적용된 새로운 Pageable
     */
    public static Pageable mapSortFields(Pageable pageable, Map<String, String> fieldMap) {
        if (pageable.getSort().isUnsorted()) {
            return pageable;
        }

        Sort mappedSort = Sort.unsorted();

        for (Sort.Order order : pageable.getSort()) {
            String property = order.getProperty();
            String mappedProperty = fieldMap.getOrDefault(property, property);

            mappedSort = mappedSort.and(Sort.by(order.getDirection(), mappedProperty));
        }

        return PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                mappedSort
        );
    }

    /**
     * Patch 엔티티용 정렬 필드 매핑
     *
     * <p>API 필드명:
     * <ul>
     *   <li>siteName -> site.siteName</li>
     *   <li>engineerName -> engineer.engineerName</li>
     * </ul>
     */
    public static Pageable mapPatchSortFields(Pageable pageable) {
        Map<String, String> fieldMap = new HashMap<>();
        fieldMap.put("siteName", "site.siteName");
        fieldMap.put("engineerName", "engineer.engineerName");
        return mapSortFields(pageable, fieldMap);
    }
}
