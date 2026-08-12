package com.ts.rm.domain.message.repository;

import com.ts.rm.domain.message.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Message Repository
 *
 * <p>메시지(발신 단위) 데이터 접근 레이어
 */
@Repository
public interface MessageRepository extends JpaRepository<Message, Long>, MessageRepositoryCustom {

    /**
     * 멱등 키 존재 여부 확인
     *
     * <p>UNIQUE 제약이 최종 방어선이고, 이 메서드는 불필요한 INSERT 시도를 줄이는 용도다.
     *
     * @param dedupKey 멱등 키
     * @return 존재 여부
     */
    boolean existsByDedupKey(String dedupKey);
}
