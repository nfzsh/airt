package com.airt.service;

import com.airt.model.AgentInstance;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话记忆管理服务
 *
 * 为每个会话中的每个 Agent 管理独立的对话记忆
 */
@Slf4j
@Service
public class ConversationMemoryService {

    /**
     * sessionId -> agentInstanceId -> ChatMemory
     */
    private final Map<String, Map<String, MessageWindowChatMemory>> sessionMemories = new ConcurrentHashMap<>();

    /**
     * 获取或创建 Agent 的对话记忆
     */
    public MessageWindowChatMemory getOrCreateMemory(String sessionId, AgentInstance agent) {
        String agentKey = agent.getInstanceId();
        int windowSize = agent.getMemoryWindowSize();

        return sessionMemories
                .computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(agentKey, k -> {
                    log.debug("Created new memory for agent {} in session {} (window={})",
                            agent.getRoleDefinition().getRoleId(), sessionId, windowSize);
                    return MessageWindowChatMemory.builder()
                            .maxMessages(windowSize)
                            .build();
                });
    }

    /**
     * 保存消息到 Agent 记忆
     *
     * @param sessionId  会话 ID
     * @param agent      Agent 实例
     * @param userMessage 用户消息
     * @param aiMessage  AI 回复
     */
    public void saveExchange(String sessionId, AgentInstance agent,
                             UserMessage userMessage, AiMessage aiMessage) {
        MessageWindowChatMemory memory = getOrCreateMemory(sessionId, agent);
        memory.add(userMessage);
        if (aiMessage != null) {
            memory.add(aiMessage);
        }
        log.debug("Saved exchange for agent {} (messages in memory: {})",
                agent.getRoleDefinition().getRoleId(), memory.messages().size());
    }

    /**
     * 获取 Agent 的记忆消息列表
     */
    public List<ChatMessage> getMessages(String sessionId, AgentInstance agent) {
        return getOrCreateMemory(sessionId, agent).messages();
    }

    /**
     * 清理会话的所有记忆
     */
    public void cleanupSession(String sessionId) {
        Map<String, MessageWindowChatMemory> memories = sessionMemories.remove(sessionId);
        if (memories != null) {
            log.debug("Cleaned up {} agent memories for session {}", memories.size(), sessionId);
        }
    }

    /**
     * 清理指定会话中某个 Agent 的记忆
     */
    public void clearAgentMemory(String sessionId, AgentInstance agent) {
        Map<String, MessageWindowChatMemory> memories = sessionMemories.get(sessionId);
        if (memories != null) {
            memories.remove(agent.getInstanceId());
        }
        if (agent.getChatMemory() != null) {
            agent.setChatMemory(null);
        }
    }

    /**
     * 构建包含记忆的完整消息列表
     *
     * @param systemPrompt 动态系统提示（不存入记忆）
     * @param memory        Agent 的对话记忆
     * @param userMessage   当前用户消息
     * @return 完整消息列表
     */
    public List<ChatMessage> buildMessageList(SystemMessage systemPrompt,
                                                MessageWindowChatMemory memory,
                                                UserMessage userMessage) {
        List<ChatMessage> messages = new java.util.ArrayList<>();
        messages.add(systemPrompt);
        messages.addAll(memory.messages());
        messages.add(userMessage);
        return messages;
    }
}
