package com.ts.rm.domain.message.repository;

import com.ts.rm.domain.message.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Message Repository Custom Interface
 *
 * <p>QueryDSL을 사용한 커스텀 쿼리
 */
public interface MessageRepositoryCustom {

    /**
     * 발신함 조회 (숨김 제외, 최신순)
     *
     * @param senderAccountId 발신자 계정 ID
     * @param keyword         제목/내용 검색어 (null 이면 전체)
     * @param pageable        페이징
     * @return 발신 메시지 페이지
     */
    Page<Message> findOutbox(Long senderAccountId, String keyword, Pageable pageable);
}
