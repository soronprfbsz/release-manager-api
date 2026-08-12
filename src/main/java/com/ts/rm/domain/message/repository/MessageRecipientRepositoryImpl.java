package com.ts.rm.domain.message.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ts.rm.domain.message.entity.MessageRecipient;
import com.ts.rm.domain.message.entity.QMessage;
import com.ts.rm.domain.message.entity.QMessageRecipient;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/**
 * MessageRecipient Repository Custom Implementation
 *
 * <p>QueryDSL을 사용한 커스텀 쿼리 구현
 */
@Repository
@RequiredArgsConstructor
public class MessageRecipientRepositoryImpl implements MessageRecipientRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    private static final QMessageRecipient messageRecipient = QMessageRecipient.messageRecipient;
    private static final QMessage message = QMessage.message;

    @Override
    public Page<MessageRecipient> findInbox(Long accountId, boolean unreadOnly, String keyword,
            Pageable pageable) {
        List<MessageRecipient> content = queryFactory
                .selectFrom(messageRecipient)
                .join(messageRecipient.message, message).fetchJoin()
                .leftJoin(message.sender).fetchJoin()
                .where(
                        messageRecipient.recipient.accountId.eq(accountId),
                        messageRecipient.deletedAt.isNull(),
                        unreadOnly ? messageRecipient.readAt.isNull() : null,
                        keywordContains(keyword)
                )
                .orderBy(message.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(messageRecipient.count())
                .from(messageRecipient)
                .join(messageRecipient.message, message)
                .where(
                        messageRecipient.recipient.accountId.eq(accountId),
                        messageRecipient.deletedAt.isNull(),
                        unreadOnly ? messageRecipient.readAt.isNull() : null,
                        keywordContains(keyword)
                );

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    /**
     * 제목 / 내용 / 발신자명 부분 일치 (검색어가 없으면 조건 미적용)
     */
    private BooleanExpression keywordContains(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        return message.title.containsIgnoreCase(keyword)
                .or(message.content.containsIgnoreCase(keyword))
                .or(message.senderName.containsIgnoreCase(keyword));
    }
}
