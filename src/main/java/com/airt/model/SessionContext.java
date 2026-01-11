package com.airt.model;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * 会话上下文
 *
 * 传递给 Agent 的上下文信息
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SessionContext {

    /**
     * 会话 ID
     */
    private String sessionId;

    /**
     * 讨论主题
     */
    private String topic;

    /**
     * 上下文描述
     */
    private String description;

    /**
     * 公共白板 JSON
     */
    private String blackboardJson;

    /**
     * 历史摘要
     */
    private String historySummary;

    /**
     * 当前轮次
     */
    private int currentRound;

    /**
     * 最大轮数
     */
    private int maxRounds;
}
