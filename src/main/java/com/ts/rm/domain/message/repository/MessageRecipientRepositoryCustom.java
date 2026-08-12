package com.ts.rm.domain.message.repository;

import com.ts.rm.domain.message.entity.MessageRecipient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * MessageRecipient Repository Custom Interface
 *
 * <p>QueryDSL을 사용한 커스텀 쿼리
 */
public interface MessageRecipientRepositoryCustom {

    /**
     * 수신함 조회 (숨김 제외, 최신순)
     *
     * @param accountId  수신자 계정 ID
     * @param unreadOnly true 면 안읽은 것만
     * @param keyword    제목/내용/발신자명 검색어 (null 이면 전체)
     * @param pageable   페이징
     * @return 수신 행 페이지 (message / sender fetch join)
     */
    Page<MessageRecipient> findInbox(Long accountId, boolean unreadOnly, String keyword,
            Pageable pageable);
}
