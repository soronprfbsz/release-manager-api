package com.ts.rm.global.websocket.interceptor;

import com.ts.rm.global.security.jwt.JwtTokenProvider;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * STOMP 인증 인터셉터
 *
 * <p>이 인터셉터는 특정 엔드포인트가 아니라 <b>STOMP inbound 채널 전체</b>에 걸린다.
 * 따라서 토큰을 보내지 않는 기존 터미널 연결({@code /ws/terminal})을 깨뜨리지 않도록,
 * CONNECT 에 토큰이 <b>있을 때만</b> Principal 을 세우고 검증 실패는 익명으로 통과시킨다.
 * 대신 개인 큐인 {@code /user/**} 구독은 Principal 을 강제한다 (ADR-0004).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String USER_DESTINATION_PREFIX = "/user/";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            authenticate(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            requirePrincipalForUserDestination(accessor);
        }

        return message;
    }

    /**
     * CONNECT 프레임의 Authorization 헤더로 Principal 을 세운다.
     *
     * <p>토큰이 없거나 유효하지 않으면 익명으로 둔다 — 인증이 필요한 구독은
     * {@link #requirePrincipalForUserDestination} 에서 걸러진다.
     */
    private void authenticate(StompHeaderAccessor accessor) {
        String token = resolveToken(accessor);
        if (!StringUtils.hasText(token)) {
            log.debug("STOMP CONNECT - 토큰 없음, 익명 연결로 진행");
            return;
        }

        if (!jwtTokenProvider.validateToken(token)) {
            log.debug("STOMP CONNECT - 유효하지 않은 토큰, 익명 연결로 진행");
            return;
        }

        Long accountId = jwtTokenProvider.getAccountId(token);
        String role = jwtTokenProvider.getRole(token);

        // Principal 이름 = accountId. 이메일은 변경될 수 있으므로 불변 식별자를 쓴다.
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(String.valueOf(accountId), null, List.of());
        accessor.setUser(authentication);

        log.debug("STOMP CONNECT 인증 완료 - accountId: {}, role: {}", accountId, role);
    }

    /**
     * 개인 큐 구독은 Principal 이 있어야 한다.
     *
     * <p>Principal 이 없으면 Spring 이 세션 기준으로 목적지를 만들어 버려
     * {@code convertAndSendToUser} 로 보낸 메시지가 도달하지 않는다. 조용히 실패하는
     * 대신 구독 자체를 거부해 원인을 드러낸다.
     */
    private void requirePrincipalForUserDestination(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith(USER_DESTINATION_PREFIX)) {
            return;
        }

        if (accessor.getUser() == null) {
            log.warn("인증 없이 개인 큐 구독 시도 - destination: {}", destination);
            throw new MessageDeliveryException("개인 알림 채널은 인증이 필요합니다.");
        }
    }

    private String resolveToken(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
