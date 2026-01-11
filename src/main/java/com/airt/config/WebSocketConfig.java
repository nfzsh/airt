package com.airt.config;

import com.airt.service.WebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * WebSocket 配置
 */
@Slf4j
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final WebSocketService webSocketService;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new RoundtableWebSocketHandler(), "/ws/roundtable/{sessionId}")
                .setAllowedOrigins("*");
    }

    private class RoundtableWebSocketHandler extends TextWebSocketHandler {

        @Override
        public void afterConnectionEstablished(WebSocketSession session) throws Exception {
            String sessionId = extractSessionId(session);
            if (sessionId != null) {
                webSocketService.registerSession(sessionId, session);
                log.info("WebSocket connection established for session {}", sessionId);
            }
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
            String sessionId = extractSessionId(session);
            if (sessionId != null) {
                webSocketService.unregisterSession(sessionId);
                log.info("WebSocket connection closed for session {}", sessionId);
            }
        }

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
            // 处理客户端消息
            String payload = message.getPayload();
            log.debug("Received message: {}", payload);
        }

        private String extractSessionId(WebSocketSession session) {
            String uri = session.getUri().toString();
            String[] parts = uri.split("/");
            return parts.length > 0 ? parts[parts.length - 1] : null;
        }
    }
}
