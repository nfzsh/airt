package com.airt.model;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 圆桌会话模型
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RoundtableSession {

    /**
     * 会话 ID
     */
    private String sessionId;

    /**
     * 讨论主题
     */
    private String topic;

    /**
     * 会话描述
     */
    private String description;

    /**
     * 上下文信息
     */
    private String context;

    /**
     * 参与角色列表
     */
    @Builder.Default
    private List<String> selectedRoles = List.of();

    /**
     * 会话状态
     */
    @Builder.Default
    private SessionState state = SessionState.INIT;

    /**
     * 当前轮次
     */
    @Builder.Default
    private int currentRound = 0;

    /**
     * 当前发言人索引（在 agents 列表中的位置）
     */
    @Builder.Default
    private int currentSpeakerIndex = 0;

    /**
     * 最大轮数
     */
    @Builder.Default
    private int maxRounds = 50;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 开始时间
     */
    private LocalDateTime startedAt;

    /**
     * 结束时间
     */
    private LocalDateTime endedAt;

    /**
     * 公共白板
     */
    private SharedBlackboard blackboard;

    /**
     * 扩展配置
     */
    @Builder.Default
    private Map<String, Object> config = Map.of();

    /**
     * 会话状态枚举
     */
    public enum SessionState {
        INIT,
        OPENING,
        DEBATE,
        CHECK_FACT,
        HUMAN_INTERVENTION,
        SYNTHESIS,
        FINISHED,
        CANCELLED
    }
}
