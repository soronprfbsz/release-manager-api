package com.ts.rm.domain.message.entity;

import com.ts.rm.domain.account.entity.Account;
import com.ts.rm.domain.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * MessageRecipient Entity
 *
 * <p>메시지 수신 단위. 읽음/숨김 상태는 수신자마다 독립적이며, 탑바 배지는
 * 이 테이블만 집계한다.
 */
@Entity
@Table(name = "message_recipient")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageRecipient extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recipient_id")
    private Long recipientId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    private Message message;

    /**
     * 수신자. 계정이 삭제되면 null 이 되고 아래 스냅샷만 남는다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_account_id")
    private Account recipient;

    @Column(name = "recipient_email", nullable = false, length = 100)
    private String recipientEmail;

    @Column(name = "recipient_name", nullable = false, length = 50)
    private String recipientName;

    /**
     * 읽은 일시 (null 이면 안읽음)
     */
    @Column(name = "read_at")
    private LocalDateTime readAt;

    /**
     * 수신함 숨김 일시 (soft delete)
     */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /**
     * 읽음 처리 — 이미 읽은 메시지는 최초 읽은 시각을 유지한다.
     */
    public void markAsRead() {
        if (this.readAt == null) {
            this.readAt = LocalDateTime.now();
        }
    }

    /**
     * 수신함에서 숨김
     */
    public void hide() {
        this.deletedAt = LocalDateTime.now();
    }
}
