package com.ts.rm.domain.account.repository;

import com.ts.rm.domain.account.entity.Account;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Account Repository Custom Interface
 *
 * <p>QueryDSL을 사용한 복잡한 계정 쿼리 인터페이스
 */
public interface AccountRepositoryCustom {

    /**
     * 계정 목록 조회 (상태 필터링 + 부서 필터링 + 키워드 검색)
     *
     * @param status 계정 상태 (null이면 전체)
     * @param departmentIds 부서 ID 목록 (null이면 전체, 하위 부서 포함 시 여러 ID 전달)
     * @param primaryDepartmentId 우선 정렬할 부서 ID (하위 부서 포함 조회 시, 해당 부서 계정이 먼저 정렬됨)
     * @param departmentType 부서 유형 (DEVELOPMENT: 개발, ENGINEER: 엔지니어)
     * @param unassigned 미배치 계정만 조회 (true: department가 null인 계정만)
     * @param keyword 검색 키워드 (계정명, 이메일)
     * @param pageable 페이징 정보
     * @return 계정 페이지
     */
    Page<Account> findAllWithFilters(String status, List<Long> departmentIds, Long primaryDepartmentId,
                                     String departmentType, boolean unassigned, String keyword, Pageable pageable);

    /**
     * 활성 관리자/운영자 연락처 목록 조회
     *
     * <p>role IN ('ADMIN','OPERATOR') AND status = 'ACTIVE' 조건으로 조회하며,
     * ADMIN 우선 → 이름 오름차순으로 정렬한다.
     *
     * @return 활성 ADMIN/OPERATOR 계정 목록
     */
    List<Account> findActiveAdminContacts();
}
