package com.ts.rm.global.websocket.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.ts.rm.global.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

/**
 * STOMP 인증 인터셉터 단위 테스트
 *
 * <p>이 인터셉터는 STOMP 채널 <b>전체</b>에 걸리므로, 토큰을 보내지 않는 기존 터미널
 * 연결이 그대로 동작하는지가 핵심 검증 대상이다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("StompAuthChannelInterceptor 테스트")
class StompAuthChannelInterceptorTest {

    private static final String VALID_TOKEN = "valid.jwt.token";

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private MessageChannel channel;

    @InjectMocks
    private StompAuthChannelInterceptor interceptor;

    @Test
    @DisplayName("CONNECT + 유효 토큰 - Principal 이름이 accountId 로 설정된다")
    void connect_WithValidToken_SetsPrincipal() {
        // given
        given(jwtTokenProvider.validateToken(VALID_TOKEN)).willReturn(true);
        given(jwtTokenProvider.getAccountId(VALID_TOKEN)).willReturn(42L);
        given(jwtTokenProvider.getRole(VALID_TOKEN)).willReturn("USER");

        StompHeaderAccessor accessor = connectAccessor("Bearer " + VALID_TOKEN);

        // when
        interceptor.preSend(toMessage(accessor), channel);

        // then
        assertThat(accessor.getUser()).isNotNull();
        assertThat(accessor.getUser().getName()).isEqualTo("42");
    }

    @Test
    @DisplayName("CONNECT + 토큰 없음 - 예외 없이 익명으로 통과한다 (터미널 호환)")
    void connect_WithoutToken_PassesAsAnonymous() {
        // given
        StompHeaderAccessor accessor = connectAccessor(null);

        // when & then
        assertThatCode(() -> interceptor.preSend(toMessage(accessor), channel))
                .doesNotThrowAnyException();
        assertThat(accessor.getUser()).isNull();
    }

    @Test
    @DisplayName("CONNECT + 무효 토큰 - 예외 없이 익명으로 통과한다")
    void connect_WithInvalidToken_PassesAsAnonymous() {
        // given
        given(jwtTokenProvider.validateToken("bad.token")).willReturn(false);
        StompHeaderAccessor accessor = connectAccessor("Bearer bad.token");

        // when & then
        assertThatCode(() -> interceptor.preSend(toMessage(accessor), channel))
                .doesNotThrowAnyException();
        assertThat(accessor.getUser()).isNull();
    }

    @Test
    @DisplayName("터미널 토픽 구독 - 인증 없이도 허용된다 (기존 동작 유지)")
    void subscribe_TerminalTopic_AllowedWithoutPrincipal() {
        // given
        StompHeaderAccessor accessor = subscribeAccessor("/topic/terminal/session-1");

        // when & then
        assertThatCode(() -> interceptor.preSend(toMessage(accessor), channel))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("개인 큐 구독 + Principal 없음 - 거부된다")
    void subscribe_UserQueue_WithoutPrincipal_Rejected() {
        // given
        StompHeaderAccessor accessor = subscribeAccessor("/user/queue/messages");

        // when & then
        assertThatThrownBy(() -> interceptor.preSend(toMessage(accessor), channel))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("인증이 필요합니다");
    }

    @Test
    @DisplayName("개인 큐 구독 + Principal 있음 - 허용된다")
    void subscribe_UserQueue_WithPrincipal_Allowed() {
        // given
        StompHeaderAccessor accessor = subscribeAccessor("/user/queue/messages");
        accessor.setUser(() -> "42");

        // when & then
        assertThatCode(() -> interceptor.preSend(toMessage(accessor), channel))
                .doesNotThrowAnyException();
    }

    // === Helpers ===

    private StompHeaderAccessor connectAccessor(String authorizationHeader) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        if (authorizationHeader != null) {
            accessor.setNativeHeader("Authorization", authorizationHeader);
        }
        return accessor;
    }

    private StompHeaderAccessor subscribeAccessor(String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setLeaveMutable(true);
        accessor.setDestination(destination);
        return accessor;
    }

    private Message<byte[]> toMessage(StompHeaderAccessor accessor) {
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
