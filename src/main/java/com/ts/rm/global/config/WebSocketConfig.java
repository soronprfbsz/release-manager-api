package com.ts.rm.global.config;

import com.ts.rm.global.websocket.interceptor.StompAuthChannelInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket 설정
 * <p>
 * STOMP over WebSocket을 사용하여 실시간 양방향 통신을 지원합니다.
 * 웹 터미널의 실시간 입출력과 사용자 개인 알림에 사용됩니다.
 * </p>
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;

    /**
     * 메시지 브로커 설정
     * <p>
     * - /topic: 브로드캐스트 메시지 (구독자 모두에게 전송)
     * - /queue: 개인 메시지 (convertAndSendToUser 대상)
     * - /app: 애플리케이션 목적지 prefix
     * - /user: 개인 목적지 prefix (클라이언트는 /user/queue/... 를 구독)
     * </p>
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Simple In-Memory 브로커 사용 — 앱 인스턴스가 1개라 충분하다.
        // (다중 인스턴스로 확장하면 RabbitMQ / Redis 브로커 필요)
        config.enableSimpleBroker("/topic", "/queue");

        // 클라이언트에서 서버로 메시지 전송 시 prefix
        config.setApplicationDestinationPrefixes("/app");

        // 개인 목적지 prefix — convertAndSendToUser 가 이 경로로 라우팅한다
        config.setUserDestinationPrefix("/user");
    }

    /**
     * STOMP 엔드포인트 등록
     * <p>
     * - /ws/terminal: 웹 터미널 (인증 없이 동작하던 기존 방식 유지)
     * - /ws/notifications: 개인 알림 (CONNECT 시 JWT 필요)
     * - SockJS fallback 지원
     * </p>
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/terminal")
                .setAllowedOriginPatterns("*") // CORS 설정 (프로덕션에서는 특정 도메인만 허용)
                .withSockJS(); // SockJS fallback 지원 (WebSocket 미지원 브라우저 대응)

        registry.addEndpoint("/ws/notifications")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    /**
     * 인바운드 채널 인터셉터 등록
     *
     * <p>인터셉터는 엔드포인트별이 아니라 STOMP 채널 전체에 적용된다.
     * 터미널 연결을 깨뜨리지 않는 책임은 인터셉터 내부에 있다
     * ({@link StompAuthChannelInterceptor}).
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }
}
