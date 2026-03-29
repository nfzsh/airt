package com.airt.model;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Agent 实例模型
 *
 * 这是从 RoleDefinition 创建的运行时实例
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AgentInstance {

    /**
     * Agent 实例 ID
     */
    private String instanceId;

    /**
     * 所属会话 ID
     */
    private String sessionId;

    /**
     * 角色定义
     */
    private RoleDefinition roleDefinition;

    /**
     * Prompt 模板
     */
    private PromptTemplate promptTemplate;

    /**
     * MCP Profile
     */
    private MCPProfile mcpProfile;

    /**
     * 使用的 LLM 模型
     */
    private String model;

    /**
     * Agent 参数
     */
    @Builder.Default
    private Map<String, Object> parameters = Map.of();

    /**
     * 发言次数
     */
    @Builder.Default
    private int speakCount = 0;

    /**
     * 最后发言时间
     */
    private Long lastSpeakTime;

    /**
     * 是否活跃
     */
    @Builder.Default
    private boolean active = true;

    /**
     * 对话记忆窗口大小（保留最近 N 条消息）
     */
    @Builder.Default
    private int memoryWindowSize = 20;

    /**
     * 对话记忆（transient，不序列化）
     */
    private transient MessageWindowChatMemory chatMemory;

    /**
     * 私信队列（God Mode whisper 注入的上下文）
     * key: agentId, value: message
     */
    private transient Queue<String> whispers;

    /**
     * 获取或创建对话记忆
     */
    public MessageWindowChatMemory getOrCreateChatMemory() {
        if (chatMemory == null) {
            chatMemory = MessageWindowChatMemory.builder()
                    .maxMessages(memoryWindowSize)
                    .build();
        }
        return chatMemory;
    }

    /**
     * 获取或创建私信队列
     */
    public Queue<String> getOrCreateWhispers() {
        if (whispers == null) {
            whispers = new ConcurrentLinkedQueue<>();
        }
        return whispers;
    }

    /**
     * 消费所有私信并拼接为文本
     */
    public String drainWhispers() {
        Queue<String> queue = getOrCreateWhispers();
        if (queue.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder("【人类给你的私信（其他角色看不到）】\n");
        while (!queue.isEmpty()) {
            sb.append("- ").append(queue.poll()).append("\n");
        }
        return sb.toString();
    }
}
