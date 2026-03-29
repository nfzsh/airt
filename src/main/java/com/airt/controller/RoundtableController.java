package com.airt.controller;

import com.airt.agent.AgentFactory;
import com.airt.config.AirtProperties;
import com.airt.dto.AgentResponse;
import com.airt.model.RoleDefinition;
import com.airt.model.RoundtableSession;
import com.airt.service.OrchestratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * REST API 控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/roundtable")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class RoundtableController {

    private final OrchestratorService orchestratorService;
    private final AgentFactory agentFactory;
    private final AirtProperties airtProperties;

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "healthy", "service", "AIRT"));
    }

    /**
     * 获取所有可用角色
     * @deprecated 使用 /api/config/roles 代替
     */
    @Deprecated
    @GetMapping("/roles")
    public ResponseEntity<List<RoleDefinition>> getRoles() {
        return ResponseEntity.ok(agentFactory.getAvailableRoles());
    }

    /**
     * 创建会话
     */
    @PostMapping("/session")
    public ResponseEntity<Map<String, String>> createSession(@RequestBody CreateSessionRequest request) {
        String sessionId = orchestratorService.createSession(
                request.getTopic(),
                request.getDescription(),
                request.getRoles()
        );
        return ResponseEntity.ok(Map.of("sessionId", sessionId));
    }

    /**
     * 获取会话信息
     */
    @GetMapping("/session/{sessionId}")
    public ResponseEntity<RoundtableSession> getSession(@PathVariable String sessionId) {
        RoundtableSession session = orchestratorService.getSession(sessionId);
        if (session == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(session);
    }

    /**
     * 启动会话
     */
    @PostMapping("/session/{sessionId}/start")
    public ResponseEntity<Map<String, String>> startSession(@PathVariable String sessionId) {
        orchestratorService.startSession(sessionId);
        return ResponseEntity.ok(Map.of("status", "started"));
    }

    /**
     * 推进讨论一轮
     */
    @PostMapping("/session/{sessionId}/proceed")
    public ResponseEntity<Map<String, String>> proceedRound(@PathVariable String sessionId) {
        orchestratorService.proceedRound(sessionId);
        return ResponseEntity.ok(Map.of("status", "proceeded"));
    }

    /**
     * 暂停会话
     */
    @PostMapping("/session/{sessionId}/pause")
    public ResponseEntity<Map<String, String>> pauseSession(@PathVariable String sessionId) {
        orchestratorService.pauseSession(sessionId);
        return ResponseEntity.ok(Map.of("status", "paused"));
    }

    /**
     * 恢复会话
     */
    @PostMapping("/session/{sessionId}/resume")
    public ResponseEntity<Map<String, String>> resumeSession(@PathVariable String sessionId) {
        orchestratorService.resumeSession(sessionId);
        return ResponseEntity.ok(Map.of("status", "resumed"));
    }

    /**
     * 结束会话
     */
    @PostMapping("/session/{sessionId}/finish")
    public ResponseEntity<Map<String, String>> finishSession(@PathVariable String sessionId) {
        orchestratorService.finishSession(sessionId);
        return ResponseEntity.ok(Map.of("status", "finished"));
    }

    /**
     * 获取讨论历史
     */
    @GetMapping("/session/{sessionId}/history")
    public ResponseEntity<List<AgentResponse>> getHistory(@PathVariable String sessionId) {
        return ResponseEntity.ok(orchestratorService.getHistory(sessionId));
    }

    /**
     * 定向提问
     */
    @PostMapping("/session/{sessionId}/question")
    public ResponseEntity<AgentResponse> directQuestion(
            @PathVariable String sessionId,
            @RequestBody DirectQuestionRequest request) {
        AgentResponse response = orchestratorService.directQuestion(sessionId, request.getRoleId(), request.getQuestion());
        return ResponseEntity.ok(response);
    }

    /**
     * 人类参与讨论
     */
    @PostMapping("/session/{sessionId}/human-input")
    public ResponseEntity<Map<String, String>> humanInput(
            @PathVariable String sessionId,
            @RequestBody HumanInputRequest request) {
        orchestratorService.addHumanInput(sessionId, request.getContent());
        return ResponseEntity.ok(Map.of("status", "added"));
    }

    /**
     * 耳语 — 对特定 Agent 私下注入上下文
     */
    @PostMapping("/session/{sessionId}/whisper")
    public ResponseEntity<Map<String, String>> whisper(
            @PathVariable String sessionId,
            @RequestBody WhisperRequest request) {
        orchestratorService.whisper(sessionId, request.getAgentId(), request.getMessage());
        return ResponseEntity.ok(Map.of("status", "whispered"));
    }

    /**
     * 指定下一个发言者
     */
    @PostMapping("/session/{sessionId}/next-speaker")
    public ResponseEntity<Map<String, String>> setNextSpeaker(
            @PathVariable String sessionId,
            @RequestBody NextSpeakerRequest request) {
        orchestratorService.setNextSpeaker(sessionId, request.getAgentId());
        return ResponseEntity.ok(Map.of("status", "next-speaker-set"));
    }

    /**
     * 改变讨论焦点
     */
    @PostMapping("/session/{sessionId}/change-focus")
    public ResponseEntity<Map<String, String>> changeFocus(
            @PathVariable String sessionId,
            @RequestBody ChangeFocusRequest request) {
        orchestratorService.changeFocus(sessionId, request.getFocus());
        return ResponseEntity.ok(Map.of("status", "focus-changed"));
    }

    /**
     * 流式推进讨论一轮（SSE）
     */
    @PostMapping(value = "/session/{sessionId}/proceed-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> proceedRoundStream(@PathVariable String sessionId) {
        log.info("Received SSE request for session {}", sessionId);
        return orchestratorService.proceedRoundStream(sessionId)
                .doOnSubscribe(subscription -> {
                    log.info("SSE stream started for session {}", sessionId);
                })
                .doOnCancel(() -> {
                    log.info("SSE stream cancelled for session {}", sessionId);
                })
                .doOnComplete(() -> {
                    log.info("SSE stream completed for session {}", sessionId);
                });
    }

    /**
     * 创建会话请求
     */
    public static class CreateSessionRequest {
        private String topic;
        private String description;
        private List<String> roles;

        public String getTopic() { return topic; }
        public void setTopic(String topic) { this.topic = topic; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public List<String> getRoles() { return roles; }
        public void setRoles(List<String> roles) { this.roles = roles; }
    }

    /**
     * 定向提问请求
     */
    public static class DirectQuestionRequest {
        private String roleId;
        private String question;

        public String getRoleId() { return roleId; }
        public void setRoleId(String roleId) { this.roleId = roleId; }
        public String getQuestion() { return question; }
        public void setQuestion(String question) { this.question = question; }
    }

    /**
     * 人类输入请求
     */
    public static class HumanInputRequest {
        private String content;
        private Long timestamp;

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public Long getTimestamp() { return timestamp; }
        public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
    }

    /**
     * 耳语请求
     */
    public static class WhisperRequest {
        private String agentId;
        private String message;

        public String getAgentId() { return agentId; }
        public void setAgentId(String agentId) { this.agentId = agentId; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    /**
     * 指定发言者请求
     */
    public static class NextSpeakerRequest {
        private String agentId;

        public String getAgentId() { return agentId; }
        public void setAgentId(String agentId) { this.agentId = agentId; }
    }

    /**
     * 改变焦点请求
     */
    public static class ChangeFocusRequest {
        private String focus;

        public String getFocus() { return focus; }
        public void setFocus(String focus) { this.focus = focus; }
    }
}
