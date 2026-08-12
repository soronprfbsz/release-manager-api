package com.ts.rm.domain.message.repository;

import com.ts.rm.domain.message.entity.MessageRecipient;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * MessageRecipient Repository
 *
 * <p>메시지 수신(읽음/숨김 상태) 데이터 접근 레이어
 */
@Repository
public interface MessageRecipientRepository
        extends JpaRepository<MessageRecipient, Long>, MessageRecipientRepositoryCustom {

    /**
     * 탑바 배지용 안읽은 메시지 개수
     *
     * @param accountId 수신자 계정 ID
     * @return 안읽고 숨기지 않은 메시지 수
     */
    long countByRecipient_AccountIdAndReadAtIsNullAndDeletedAtIsNull(Long accountId);

    /**
     * 특정 메시지에 대한 내 수신 행 조회
     *
     * @param messageId 메시지 ID
     * @param accountId 수신자 계정 ID
     * @return 수신 행 (수신자가 아니면 empty)
     */
    Optional<MessageRecipient> findByMessage_MessageIdAndRecipient_AccountId(
            Long messageId, Long accountId);

    /**
     * 여러 메시지의 수신자 일괄 조회 (발신함 목록의 N+1 방지)
     *
     * <p>수신자 부서명까지 함께 노출하므로 account / department 를 같이 끌어온다.
     *
     * @param messageIds 메시지 ID 목록
     * @return 수신 행 목록
     */
    @EntityGraph(attributePaths = {"recipient", "recipient.department"})
    List<MessageRecipient> findByMessage_MessageIdIn(List<Long> messageIds);

    /**
     * 참조 대상 기준 미숨김 수신 행 조회
     *
     * <p>패치가 처리/삭제되면 그 패치를 참조하는 독촉을 일괄 숨기기 위해 사용한다.
     *
     * @param refType 참조 대상 유형 (예: PATCH)
     * @param refId   참조 대상 ID
     * @return 아직 숨기지 않은 수신 행 목록
     */
    List<MessageRecipient> findByMessage_RefTypeAndMessage_RefIdAndDeletedAtIsNull(
            String refType, Long refId);
}
