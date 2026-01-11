package com.airt.service;

import com.airt.dto.AgentResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 服务
 *
 * 管理 WebSocket 连接和消息推送
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketService {

    private final ObjectMapper objectMapper;

    /**
     * 会话 ID -> WebSocket Session 映射
     */
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    /**
     * 注册 WebSocket 会话
     *
     * @param sessionId 会话 ID
     * @param session WebSocket 会话
     */
    public void registerSession(String sessionId, WebSocketSession session) {
        sessions.put(sessionId, session);
        log.info("Registered WebSocket session for {}", sessionId);
    }

    /**
     * 注销 WebSocket 会话
     *
     * @param sessionId 会话 ID
     */
    public void unregisterSession(String sessionId) {
        sessions.remove(sessionId);
        log.info("Unregistered WebSocket session for {}", sessionId);
    }

    /**
     * 发送 Agent 响应
     *
     * @param sessionId 会话 ID
     * @param response Agent 响应
     */
    public void sendAgentResponse(String sessionId, AgentResponse response) {
        sendToSession(sessionId, Map.of(
                "type", "AGENT_RESPONSE",
                "data", response
        ));
    }

    /**
     * 发送会话事件
     *
     * @param sessionId 会话 ID
     * @param eventType 事件类型
     * @param data 事件数据
     */
    public void sendSessionEvent(String sessionId, String eventType, Object data) {
        sendToSession(sessionId, Map.of(
                "type", eventType,
                "data", data
        ));
    }

    /**
     * 发送消息到会话
     */
    private void sendToSession(String sessionId, Object message) {
        WebSocketSession session = sessions.get(sessionId);
        if (session != null && session.isOpen()) {
            try {
                String json = objectMapper.writeValueAsString(message);
                session.sendMessage(new TextMessage(json));
            } catch (IOException e) {
                log.error("Error sending message to session {}", sessionId, e);
            }
        }
    }
}
