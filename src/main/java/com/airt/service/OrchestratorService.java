package com.airt.service;

import com.airt.agent.AgentFactory;
import com.airt.agent.AgentRuntime;
import com.airt.dto.AgentResponse;
import com.airt.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 核心调度器服务
 *
 * 负责控制整个讨论流程
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrchestratorService {

    private final AgentFactory agentFactory;
    private final AgentRuntime agentRuntime;
    private final AgentSelector agentSelector;
    private final BlackboardService blackboardService;
    private final WebSocketService webSocketService;

    /**
     * 会话存储
     */
    private final Map<String, RoundtableSession> sessions = new ConcurrentHashMap<>();

    /**
     * Agent 实例存储
     */
    private final Map<String, List<AgentInstance>> sessionAgents = new ConcurrentHashMap<>();

    /**
     * 讨论历史存储
     */
    private final Map<String, List<AgentResponse>> discussionHistory = new ConcurrentHashMap<>();

    /**
     * 创建新会话
     *
     * @param topic 讨论主题
     * @param description 描述
     * @param roleIds 角色列表
     * @return 会话 ID
     */
    public String createSession(String topic, String description, List<String> roleIds) {
        String sessionId = UUID.randomUUID().toString();

        // 创建会话
        RoundtableSession session = RoundtableSession.builder()
                .sessionId(sessionId)
                .topic(topic)
                .description(description)
                .selectedRoles(roleIds)
                .state(RoundtableSession.SessionState.INIT)
                .currentRound(0)
                .maxRounds(50)
                .createdAt(LocalDateTime.now())
                .blackboard(SharedBlackboard.builder()
                        .sessionId(sessionId)
                        .currentTopic(topic)
                        .build())
                .build();

        sessions.put(sessionId, session);

        // 创建 Agent 实例
        List<AgentInstance> agents = agentFactory.createAgents(sessionId, roleIds);
        sessionAgents.put(sessionId, agents);

        // 初始化历史记录
        discussionHistory.put(sessionId, new ArrayList<>());

        log.info("Created session {} with {} agents", sessionId, agents.size());
        return sessionId;
    }

    /**
     * 启动会话
     *
     * @param sessionId 会话 ID
     */
    public void startSession(String sessionId) {
        RoundtableSession session = sessions.get(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("Session not found: " + sessionId);
        }

        session.setState(RoundtableSession.SessionState.OPENING);
        session.setStartedAt(LocalDateTime.now());

        // 推送会话开始事件
        webSocketService.sendSessionEvent(sessionId, "SESSION_STARTED",
                "Discussion started with topic: " + session.getTopic());

        log.info("Started session {}", sessionId);
    }

    /**
     * 推进讨论一轮
     *
     * @param sessionId 会话 ID
     */
    public void proceedRound(String sessionId) {
        RoundtableSession session = sessions.get(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("Session not found: " + sessionId);
        }

        // 检查状态
        if (session.getState() == RoundtableSession.SessionState.FINISHED) {
            log.warn("Session {} is already finished", sessionId);
            return;
        }

        // 更新状态
        session.setState(RoundtableSession.SessionState.DEBATE);

        // 获取所有 Agent
        List<AgentInstance> agents = sessionAgents.get(sessionId);
        if (agents == null || agents.isEmpty()) {
            log.error("No agents found for session {}", sessionId);
            return;
        }

        // 按顺序选择下一个发言者
        AgentInstance nextSpeaker = agentSelector.selectNextSpeakerInOrder(agents, session.getCurrentSpeakerIndex());

        // 检查是否需要递增轮次（当所有角色都发言完毕后，索引回到0）
        if (session.getCurrentSpeakerIndex() == 0 && !discussionHistory.getOrDefault(sessionId, List.of()).isEmpty()) {
            session.setCurrentRound(session.getCurrentRound() + 1);
            log.info("All agents have spoken, advancing to round {}", session.getCurrentRound());
        }

        // 检查是否超过最大轮数
        if (session.getCurrentRound() > session.getMaxRounds()) {
            finishSession(sessionId);
            return;
        }

        // 获取历史记录
        List<AgentResponse> history = discussionHistory.get(sessionId);
        String previousSpeaker = history.isEmpty() ? null : history.get(history.size() - 1).getRoleDisplayName();

        // 构建上下文
        SessionContext context = SessionContext.builder()
                .sessionId(sessionId)
                .topic(session.getTopic())
                .description(session.getDescription())
                .blackboardJson(blackboardService.toJson(session.getBlackboard()))
                .historySummary(buildHistorySummary(history))
                .currentRound(session.getCurrentRound())
                .maxRounds(session.getMaxRounds())
                .build();

        // 执行 Agent
        AgentResponse response = agentRuntime.execute(nextSpeaker, context, previousSpeaker);

        // 更新历史记录
        history.add(response);
        discussionHistory.put(sessionId, history);

        // 更新白板
        blackboardService.updateFromResponse(session.getBlackboard(), response);

        // 更新 Agent 发言次数
        nextSpeaker.setSpeakCount(nextSpeaker.getSpeakCount() + 1);
        nextSpeaker.setLastSpeakTime(System.currentTimeMillis());

        // 推进到下一个发言人
        session.setCurrentSpeakerIndex(session.getCurrentSpeakerIndex() + 1);

        // 推送响应
        webSocketService.sendAgentResponse(sessionId, response);

        log.info("Speaker [{}]: {} completed in session {}, round: {}, speakerIndex: {}",
                session.getCurrentSpeakerIndex() - 1,
                nextSpeaker.getRoleDefinition().getDisplayName(),
                sessionId,
                session.getCurrentRound(),
                session.getCurrentSpeakerIndex() - 1);
    }

    /**
     * 暂停会话
     *
     * @param sessionId 会话 ID
     */
    public void pauseSession(String sessionId) {
        RoundtableSession session = sessions.get(sessionId);
        if (session != null) {
            session.setState(RoundtableSession.SessionState.HUMAN_INTERVENTION);
            webSocketService.sendSessionEvent(sessionId, "SESSION_PAUSED", "Discussion paused");
            log.info("Paused session {}", sessionId);
        }
    }

    /**
     * 恢复会话
     *
     * @param sessionId 会话 ID
     */
    public void resumeSession(String sessionId) {
        RoundtableSession session = sessions.get(sessionId);
        if (session != null) {
            session.setState(RoundtableSession.SessionState.DEBATE);
            webSocketService.sendSessionEvent(sessionId, "SESSION_RESUMED", "Discussion resumed");
            log.info("Resumed session {}", sessionId);
        }
    }

    /**
     * 结束会话
     *
     * @param sessionId 会话 ID
     */
    public void finishSession(String sessionId) {
        RoundtableSession session = sessions.get(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("Session not found: " + sessionId);
        }

        session.setState(RoundtableSession.SessionState.SYNTHESIS);

        // 生成最终报告
        Map<String, Object> report = generateFinalReport(session);

        session.setState(RoundtableSession.SessionState.FINISHED);
        session.setEndedAt(LocalDateTime.now());

        // 推送报告
        webSocketService.sendSessionEvent(sessionId, "SESSION_FINISHED", report);

        log.info("Finished session {}", sessionId);
    }

    /**
     * 定向提问
     *
     * @param sessionId 会话 ID
     * @param roleId 角色 ID
     * @param question 问题
     */
    public void directQuestion(String sessionId, String roleId, String question) {
        // TODO: 实现定向提问
        log.info("Direct question to role {} in session {}: {}", roleId, sessionId, question);
    }

    /**
     * 获取会话信息
     *
     * @param sessionId 会话 ID
     * @return 会话信息
     */
    public RoundtableSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    /**
     * 获取讨论历史
     *
     * @param sessionId 会话 ID
     * @return 历史记录
     */
    public List<AgentResponse> getHistory(String sessionId) {
        return discussionHistory.getOrDefault(sessionId, List.of());
    }

    /**
     * 构建历史摘要
     * 包含之前所有发言者的完整内容
     */
    private String buildHistorySummary(List<AgentResponse> history) {
        if (history.isEmpty()) {
            return "No previous discussion. You are the first speaker.";
        }

        StringBuilder summary = new StringBuilder();
        summary.append("【Previous Discussion Summary】\n\n");

        // 按发言顺序列出所有之前的发言
        for (int i = 0; i < history.size(); i++) {
            AgentResponse response = history.get(i);
            summary.append(String.format("[%d] %s:\n", i + 1, response.getRoleDisplayName()));

            // 获取完整的公开响应
            String content = response.getPublicResponse();
            if (content != null && !content.isEmpty()) {
                // 限制每个响应最多500字符，避免上下文过长
                String truncated = content.length() > 500 ? content.substring(0, 500) + "..." : content;
                summary.append(truncated).append("\n");
            }

            // 如果有思考过程，也包含在内（但简要）
            if (response.getThinkingProcess() != null && !response.getThinkingProcess().isEmpty()) {
                String thinking = response.getThinkingProcess();
                String briefThinking = thinking.length() > 200 ? thinking.substring(0, 200) + "..." : thinking;
                summary.append("(Thought: ").append(briefThinking).append(")\n");
            }

            summary.append("\n");
        }

        summary.append("【End of Previous Discussion】\n");
        summary.append("Please respond to the points above, build upon the discussion, or introduce new perspectives as appropriate.");

        return summary.toString();
    }

    /**
     * 生成最终报告
     */
    private Map<String, Object> generateFinalReport(RoundtableSession session) {
        Map<String, Object> report = new HashMap<>();
        report.put("sessionId", session.getSessionId());
        report.put("topic", session.getTopic());
        report.put("rounds", session.getCurrentRound());
        report.put("summary", "Discussion completed");
        report.put("blackboard", session.getBlackboard());
        return report;
    }

    /**
     * 添加人类输入到讨论中
     *
     * @param sessionId 会话 ID
     * @param content 人类输入内容
     */
    public void addHumanInput(String sessionId, String content) {
        RoundtableSession session = sessions.get(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("Session not found: " + sessionId);
        }

        // 创建人类响应
        AgentResponse humanResponse = AgentResponse.builder()
                .agentInstanceId("HUMAN-" + sessionId)
                .roleName("human")
                .roleDisplayName("👤 人类")
                .thinkingProcess("")
                .publicResponse(content)
                .keyInsights(List.of())
                .toolCalls(List.of())
                .timestamp(System.currentTimeMillis())
                .metadata(Map.of(
                        "isHuman", true,
                        "type", "human_input"
                ))
                .build();

        // 添加到历史记录
        List<AgentResponse> history = discussionHistory.get(sessionId);
        if (history != null) {
            history.add(humanResponse);
        }

        // 推送到前端
        webSocketService.sendAgentResponse(sessionId, humanResponse);

        log.info("Human input added to session {}: {}", sessionId, content);
    }

    /**
     * 流式推进讨论一轮
     *
     * @param sessionId 会话 ID
     * @return 流式 token 输出
     */
    public Flux<String> proceedRoundStream(String sessionId) {
        RoundtableSession session = sessions.get(sessionId);
        if (session == null) {
            return Flux.error(new IllegalArgumentException("Session not found: " + sessionId));
        }

        // 检查会话状态是否允许推进讨论
        RoundtableSession.SessionState state = session.getState();
        if (state == RoundtableSession.SessionState.FINISHED ||
            state == RoundtableSession.SessionState.CANCELLED) {
            return Flux.error(new IllegalStateException("Session is " + state + ", cannot proceed"));
        }

        // 如果是 INIT 或 OPENING 状态，自动更新为 DEBATE
        if (state == RoundtableSession.SessionState.INIT ||
            state == RoundtableSession.SessionState.OPENING) {
            session.setState(RoundtableSession.SessionState.DEBATE);
            log.info("Session {} state changed from {} to DEBATE", sessionId, state);
        }

        // 获取所有 Agent
        List<AgentInstance> agents = sessionAgents.get(sessionId);
        if (agents == null || agents.isEmpty()) {
            return Flux.error(new IllegalStateException("No agents found for session: " + sessionId));
        }

        // 按顺序选择下一个发言者
        AgentInstance nextSpeaker = agentSelector.selectNextSpeakerInOrder(agents, session.getCurrentSpeakerIndex());

        // 检查是否需要递增轮次（当所有角色都发言完毕后，索引回到0）
        if (session.getCurrentSpeakerIndex() == 0 && !discussionHistory.getOrDefault(sessionId, List.of()).isEmpty()) {
            session.setCurrentRound(session.getCurrentRound() + 1);
            log.info("All agents have spoken, advancing to round {}", session.getCurrentRound());
        }

        // 检查是否超过最大轮数
        if (session.getCurrentRound() > session.getMaxRounds()) {
            return Flux.error(new IllegalStateException("Session has reached max rounds"));
        }

        List<AgentResponse> history = discussionHistory.getOrDefault(sessionId, new ArrayList<>());

        // 获取上一位发言者
        String previousSpeaker = history.isEmpty() ? null : history.get(history.size() - 1).getRoleDisplayName();

        // 构建上下文
        SessionContext context = SessionContext.builder()
                .sessionId(sessionId)
                .topic(session.getTopic())
                .description(session.getDescription())
                .blackboardJson(blackboardService.toJson(session.getBlackboard()))
                .historySummary(buildHistorySummary(history))
                .currentRound(session.getCurrentRound())
                .maxRounds(session.getMaxRounds())
                .build();

        log.info("Starting stream for round {}, speaker [{}]: {} in session {}",
                session.getCurrentRound(),
                session.getCurrentSpeakerIndex(),
                nextSpeaker.getRoleDefinition().getDisplayName(),
                sessionId);

        // 返回流式执行，并传入回调处理响应
        return agentRuntime.streamExecute(nextSpeaker, context, previousSpeaker, agentResponse -> {
            // 响应完成后的回调
            log.info("Response received for round {}, speaker [{}]: {} in session {}",
                    session.getCurrentRound(),
                    session.getCurrentSpeakerIndex(),
                    nextSpeaker.getRoleDefinition().getDisplayName(),
                    sessionId);

            // 添加到历史记录
            List<AgentResponse> currentHistory = discussionHistory.getOrDefault(sessionId, new ArrayList<>());
            currentHistory.add(agentResponse);
            discussionHistory.put(sessionId, currentHistory);

            // 更新白板
            blackboardService.updateFromResponse(session.getBlackboard(), agentResponse);

            // 更新 Agent 发言次数
            nextSpeaker.setSpeakCount(nextSpeaker.getSpeakCount() + 1);
            nextSpeaker.setLastSpeakTime(System.currentTimeMillis());

            // 推送响应到 WebSocket
            webSocketService.sendAgentResponse(sessionId, agentResponse);

            // 推进到下一个发言人
            session.setCurrentSpeakerIndex(session.getCurrentSpeakerIndex() + 1);
        })
        .doOnError(error -> {
            log.error("Stream error for session {}: {}", sessionId, error.getMessage());
        });
    }

    private String truncate(String str, int max) {
        return str.length() <= max ? str.substring(0, max) : str + "...";
    }
}
