# 신규 메시지 실시간 알림은 기존 STOMP WebSocket 을 재사용한다

수신 즉시 탑바 배지를 갱신해야 한다. 후보는 폴링 단독 / SSE 신설 / 기존 STOMP 재사용 세 가지였다.

**결정**: 기존 `WebSocketConfig` 에 `/ws/notifications` 엔드포인트를 추가하고, STOMP CONNECT 시 JWT 를 검증해 `Principal` 을 세운 뒤 `convertAndSendToUser` 로 개인 큐에 푸시한다. 폴링은 제거하지 않고 5분 백업으로만 남긴다.

## Considered Options
- **1분 폴링 단독**: 구현이 가장 작지만 최대 60초 지연이 남고, 모든 폴링 요청이 `api_log` 에 적재된다(25명 × 8시간 기준 하루 약 1.2만 행). WebSocket 인증 부재도 그대로 남는다.
- **SSE 신설**: 단방향이라 개념은 단순하나 `EventSource` 가 `Authorization` 헤더를 실을 수 없다. 이 시스템의 access token 은 in-memory 보관이라 토큰을 쿼리스트링으로 넘겨야 하고, 그 URL 이 `api_log.query_string` 에 그대로 남는다. nginx `proxy_buffering off` 도 추가로 필요하다.
- **기존 STOMP 재사용**(채택): 백엔드 `spring-boot-starter-websocket`, 프론트 `@stomp/stompjs` + `sockjs-client`, 운영 nginx `location /ws/` 프록시가 모두 이미 있다. 앱 인스턴스가 1개라 In-memory SimpleBroker 로 충분하다.

## Consequences
- `enableSimpleBroker` 에 `/queue` 를 추가하고 `setUserDestinationPrefix("/user")` 를 설정한다.
- `configureClientInboundChannel` 의 인터셉터는 **STOMP 전역**에 걸린다. 토큰을 보내지 않는 기존 터미널 연결을 깨뜨리지 않기 위해, CONNECT 에 토큰이 있을 때만 Principal 을 세우고 `/user/**` 구독에서만 Principal 을 강제한다.
- `/ws/**` 가 `permitAll` 인 상태와 터미널 채널의 인증 부재는 이번 범위 밖으로 남긴다 — 별도 과제.
- 푸시는 트랜잭션 커밋 이후(`afterCommit`)에 실행한다. 푸시 실패가 메시지 저장을 되돌려서는 안 된다.
- 폴링 5분 + STOMP 재연결(`onConnect`) 시 배지 재조회 + 창 포커스 시 재조회 조합으로, 연결이 조용히 끊긴 경우에도 최대 5분 안에 복구된다.
