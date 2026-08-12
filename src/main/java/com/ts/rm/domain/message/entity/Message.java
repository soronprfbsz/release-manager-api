package com.ts.rm.domain.message.entity;

import com.ts.rm.domain.account.entity.Account;
import com.ts.rm.domain.common.entity.BaseEntity;
import com.ts.rm.domain.message.enums.MessageType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Message Entity
 *
 * <p>메시지 발신 단위. 수신자별 상태는 {@link MessageRecipient} 가 갖는다.
 *
 * <p>시스템 발신 알림(패치 독촉 등)도 {@code messageType} 만 다른 일반 메시지로
 * 저장한다 — 발신자 역시 실재 계정이다 (ADR-0003).
 */
@Entity
@Table(name = "message")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Message extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "message_id")
    private Long messageId;

    /**
     * 발신자. 계정이 삭제되면 null 이 되고 아래 스냅샷만 남는다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_account_id")
    private Account sender;

    @Column(name = "sender_email", nullable = false, length = 100)
    private String senderEmail;

    @Column(name = "sender_name", nullable = false, length = 50)
    private String senderName;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 30)
    @Builder.Default
    private MessageType messageType = MessageType.USER;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * 참조 대상 유형 (현재는 {@code PATCH} 만)
     */
    @Column(name = "ref_type", length = 30)
    private String refType;

    @Column(name = "ref_id")
    private Long refId;

    /**
     * 딥링크용 스냅샷 — 참조 대상이 삭제된 뒤에도 화면 이동이 가능해야 하므로
     * 조회가 아닌 값으로 보관한다.
     */
    @Column(name = "ref_project_id", length = 50)
    private String refProjectId;

    @Column(name = "ref_release_type", length = 20)
    private String refReleaseType;

    /**
     * 시스템 발송 멱등 키. 사용자 쪽지는 null (MariaDB 는 NULL 중복을 허용).
     */
    @Column(name = "dedup_key", length = 100)
    private String dedupKey;

    /**
     * 발신함 숨김 일시 (soft delete)
     */
    @Column(name = "sender_deleted_at")
    private LocalDateTime senderDeletedAt;

    @OneToMany(
            mappedBy = "message",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @Builder.Default
    private List<MessageRecipient> recipients = new ArrayList<>();

    /**
     * 수신자 추가 (양방향 연관관계 편의 메서드)
     *
     * <p>계정 삭제 후에도 발신함에서 수신자를 보여줄 수 있도록 이름/이메일을
     * 스냅샷으로 함께 남긴다.
     */
    public void addRecipient(Account account) {
        MessageRecipient recipient = MessageRecipient.builder()
                .recipient(account)
                .recipientEmail(account.getEmail())
                .recipientName(account.getAccountName())
                .build();
        recipient.setMessage(this);
        this.recipients.add(recipient);
    }

    /**
     * 발신함에서 숨김
     */
    public void hideForSender() {
        this.senderDeletedAt = LocalDateTime.now();
    }
}
