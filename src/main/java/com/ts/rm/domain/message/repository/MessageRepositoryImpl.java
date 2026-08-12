package com.ts.rm.domain.message.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ts.rm.domain.message.entity.Message;
import com.ts.rm.domain.message.entity.QMessage;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/**
 * Message Repository Custom Implementation
 *
 * <p>QueryDSL을 사용한 커스텀 쿼리 구현
 */
@Repository
@RequiredArgsConstructor
public class MessageRepositoryImpl implements MessageRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    private static final QMessage message = QMessage.message;

    @Override
    public Page<Message> findOutbox(Long senderAccountId, String keyword, Pageable pageable) {
        List<Message> content = queryFactory
                .selectFrom(message)
                .leftJoin(message.sender).fetchJoin()
                .where(
                        message.sender.accountId.eq(senderAccountId),
                        message.senderDeletedAt.isNull(),
                        keywordContains(keyword)
                )
                .orderBy(message.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(message.count())
                .from(message)
                .where(
                        message.sender.accountId.eq(senderAccountId),
                        message.senderDeletedAt.isNull(),
                        keywordContains(keyword)
                );

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    /**
     * 제목 또는 내용 부분 일치 (검색어가 없으면 조건 미적용)
     */
    private BooleanExpression keywordContains(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        return message.title.containsIgnoreCase(keyword)
                .or(message.content.containsIgnoreCase(keyword));
    }
}
