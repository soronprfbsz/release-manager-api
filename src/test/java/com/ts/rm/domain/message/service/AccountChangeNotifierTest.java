package com.ts.rm.domain.message.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.ts.rm.domain.account.entity.Account;
import com.ts.rm.domain.account.repository.AccountRepository;
import com.ts.rm.domain.message.entity.Message;
import com.ts.rm.domain.message.enums.MessageType;
import com.ts.rm.domain.message.repository.MessageRepository;
import com.ts.rm.domain.common.entity.Code;
import com.ts.rm.domain.common.repository.CodeRepository;
import com.ts.rm.global.exception.BusinessException;
import com.ts.rm.global.exception.ErrorCode;
import com.ts.rm.global.security.SecurityUtil;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * AccountChangeNotifier 단위 테스트
 *
 * <p>발신자는 시스템 계정이고 작업자는 본문에 담긴다는 규칙, 그리고 "실제 변경이 있을 때만
 * 발송" 규칙을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AccountChangeNotifier 테스트")
class AccountChangeNotifierTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private CodeRepository codeRepository;

    @Mock
    private MessageNotificationPublisher notificationPublisher;

    @Captor
    private ArgumentCaptor<Message> messageCaptor;

    @InjectMocks
    private AccountChangeNotifier notifier;

    private Account systemSender;
    private Account actor;
    private Account target;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(notifier, "systemSenderEmail", "system@test.com");

        systemSender = Account.builder()
                .accountId(1L)
                .email("system@test.com")
                .accountName("시스템 관리자")
                .role("ADMIN")
                .status("ACTIVE")
                .build();

        actor = Account.builder()
                .accountId(41L)
                .email("jhlee@example.com")
                .accountName("이준호")
                .position("DEPUTY_MANAGER")
                .role("ADMIN")
                .status("ACTIVE")
                .build();

        target = Account.builder()
                .accountId(45L)
                .email("target@example.com")
                .accountName("정주원")
                .role("GUEST")
                .status("ACTIVE")
                .build();
    }

    @Test
    @DisplayName("계정 변경 통지 - 시스템 계정이 발신하고 작업자는 본문에 담는다")
    void notifyAccountUpdated_SendsFromSystemWithActorInContent() {
        // given
        given(accountRepository.findById(41L)).willReturn(Optional.of(actor));
        given(accountRepository.findByEmail("system@test.com")).willReturn(Optional.of(systemSender));
        given(messageRepository.save(any(Message.class))).willAnswer(call -> call.getArgument(0));

        try (MockedStatic<SecurityUtil> securityUtil = Mockito.mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentAccountId).thenReturn(41L);

            // when
            notifier.notifyAccountUpdated(target, List.of(
                    new AccountChangeNotifier.FieldChange("권한", "게스트", "일반 사용자"),
                    new AccountChangeNotifier.FieldChange("부서", "미배치", "서비스기술팀")));
        }

        // then
        then(messageRepository).should(times(1)).save(messageCaptor.capture());
        Message saved = messageCaptor.getValue();

        assertThat(saved.getMessageType()).isEqualTo(MessageType.ACCOUNT_UPDATED);
        assertThat(saved.getSender()).isEqualTo(systemSender);
        assertThat(saved.getSenderName()).isEqualTo("시스템 관리자");
        assertThat(saved.getRefType()).isEqualTo("ACCOUNT");
        assertThat(saved.getRefId()).isEqualTo(45L);
        assertThat(saved.getRecipients()).hasSize(1);
        assertThat(saved.getRecipients().get(0).getRecipient()).isEqualTo(target);
        assertThat(saved.getContent())
                .contains("이준호")
                .contains("· 권한: 게스트 → 일반 사용자")
                .contains("· 부서: 미배치 → 서비스기술팀")
                .contains("문의사항은 이준호 님에게 문의해 주세요.");

        then(notificationPublisher).should(times(1)).publishAfterCommit(any(), any());
    }

    @Test
    @DisplayName("계정 변경 통지 - 변경 항목이 없으면 발송하지 않는다")
    void notifyAccountUpdated_NoChanges_DoesNotSend() {
        // when
        notifier.notifyAccountUpdated(target, List.of());

        // then
        then(messageRepository).should(never()).save(any());
        then(accountRepository).should(never()).findById(any());
    }

    @Test
    @DisplayName("계정 변경 통지 - 본인이 본인을 수정하면 발송하지 않는다")
    void notifyAccountUpdated_SelfEdit_DoesNotSend() {
        try (MockedStatic<SecurityUtil> securityUtil = Mockito.mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentAccountId).thenReturn(45L);

            // when
            notifier.notifyAccountUpdated(target, List.of(
                    new AccountChangeNotifier.FieldChange("연락처", "없음", "010-0000-0000")));
        }

        // then
        then(messageRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("계정 변경 통지 - 인증 주체가 없으면 계정 변경을 실패시키지 않고 건너뛴다")
    void notifyAccountUpdated_NoAuthentication_SkipsSilently() {
        try (MockedStatic<SecurityUtil> securityUtil = Mockito.mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentAccountId)
                    .thenThrow(new BusinessException(ErrorCode.INVALID_CREDENTIALS));

            // when - 예외가 밖으로 새지 않아야 한다
            notifier.notifyAccountUpdated(target, List.of(
                    new AccountChangeNotifier.FieldChange("권한", "게스트", "일반 사용자")));
        }

        // then
        then(messageRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("계정 변경 통지 - 시스템 발신 계정이 없으면 계정 변경을 실패시키지 않고 건너뛴다")
    void notifyAccountUpdated_NoSystemSender_SkipsSilently() {
        // given
        given(accountRepository.findById(41L)).willReturn(Optional.of(actor));
        given(accountRepository.findByEmail("system@test.com")).willReturn(Optional.empty());

        try (MockedStatic<SecurityUtil> securityUtil = Mockito.mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentAccountId).thenReturn(41L);

            // when - 예외가 밖으로 새지 않아야 한다
            notifier.notifyAccountUpdated(target, List.of(
                    new AccountChangeNotifier.FieldChange("권한", "게스트", "일반 사용자")));
        }

        // then
        then(messageRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("비밀번호 초기화 통지 - 임시 비밀번호를 본문에 담지 않는다")
    void notifyPasswordReset_DoesNotLeakTemporaryPassword() {
        // given
        given(accountRepository.findById(41L)).willReturn(Optional.of(actor));
        given(accountRepository.findByEmail("system@test.com")).willReturn(Optional.of(systemSender));
        given(messageRepository.save(any(Message.class))).willAnswer(call -> call.getArgument(0));

        try (MockedStatic<SecurityUtil> securityUtil = Mockito.mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentAccountId).thenReturn(41L);

            // when
            notifier.notifyPasswordReset(target);
        }

        // then
        then(messageRepository).should(times(1)).save(messageCaptor.capture());
        Message saved = messageCaptor.getValue();

        assertThat(saved.getMessageType()).isEqualTo(MessageType.PASSWORD_RESET_DONE);
        assertThat(saved.getSender()).isEqualTo(systemSender);
        assertThat(saved.getContent())
                .contains("님이 회원님의 비밀번호를 초기화했습니다")
                .contains("임시 비밀번호는 보안상 이 쪽지에 담지 않습니다")
                .contains("이준호 님에게 직접 전달받은 임시 비밀번호로");
    }

    @Test
    @DisplayName("작업자 표기 - 직급 코드는 표시명으로 바꾼다")
    void describeActor_ResolvesPositionName() {
        // given
        given(accountRepository.findById(41L)).willReturn(Optional.of(actor));
        given(accountRepository.findByEmail("system@test.com")).willReturn(Optional.of(systemSender));
        given(messageRepository.save(any(Message.class))).willAnswer(call -> call.getArgument(0));
        given(codeRepository.findByCodeTypeIdAndCodeId("POSITION", "DEPUTY_MANAGER"))
                .willReturn(Optional.of(codeNamed("차장")));

        try (MockedStatic<SecurityUtil> securityUtil = Mockito.mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentAccountId).thenReturn(41L);

            // when
            notifier.notifyAccountUpdated(target, List.of(
                    new AccountChangeNotifier.FieldChange("권한", "게스트", "일반 사용자")));
        }

        // then
        then(messageRepository).should(times(1)).save(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getContent()).startsWith("이준호(차장) 님이");
    }

    private Code codeNamed(String codeName) {
        return Code.builder().codeName(codeName).build();
    }
}
