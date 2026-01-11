package com.airt.model;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;

/**
 * 公共白板模型
 *
 * 所有 Agent 共享的认知状态
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SharedBlackboard {

    /**
     * 会话 ID
     */
    private String sessionId;

    /**
     * 当前话题
     */
    private String currentTopic;

    /**
     * 关键洞察列表
     */
    @Builder.Default
    private List<KeyInsight> keyInsights = new ArrayList<>();

    /**
     * 待验证的问题列表
     */
    @Builder.Default
    private List<String> pendingQuestions = new ArrayList<>();

    /**
     * 事实日志
     */
    @Builder.Default
    private List<FactLog> factLog = new ArrayList<>();

    /**
     * 共识点
     */
    @Builder.Default
    private List<String> consensusPoints = new ArrayList<>();

    /**
     * 主要分歧点
     */
    @Builder.Default
    private List<ConflictPoint> conflictPoints = new ArrayList<>();

    /**
     * 讨论摘要（由记录员维护）
     */
    private String discussionSummary;

    /**
     * 上次更新时间
     */
    private long lastUpdated;

    /**
     * 关键洞察
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class KeyInsight {
        private String id;
        private String text;
        private String proponent;
        private InsightStatus status;
        @Builder.Default
        private List<String> counterArguments = new ArrayList<>();
        private Evidence evidence;

        public enum InsightStatus {
            PROPOSED,      // 已提出
            CONTESTED,     // 争议中
            ACCEPTED,      // 已共识
            REJECTED,      // 已拒绝
            PENDING_VERIFICATION // 待验证
        }
    }

    /**
     * 证据
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Evidence {
        private String source;
        private String url;
        private String data;
        private boolean verified;
    }

    /**
     * 事实日志
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class FactLog {
        private String id;
        private String claim;
        private String verifiedBy;
        private boolean isTrue;
        private String evidence;
        private long timestamp;
    }

    /**
     * 冲突点
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ConflictPoint {
        private String id;
        private String topic;
        private List<String> proponents;
        private List<String> opponents;
        private String reason;
        private ConflictType type;

        public enum ConflictType {
            FACTUAL_DISAGREEMENT,  // 事实分歧
            VALUE_DIFFERENCE,      // 价值观差异
            PRIORITY_CONFLICT,     // 优先级冲突
            ASSUMPTION_CLASH       // 假设冲突
        }
    }

    /**
     * 添加洞察
     */
    public void addInsight(KeyInsight insight) {
        this.keyInsights.add(insight);
        this.lastUpdated = System.currentTimeMillis();
    }

    /**
     * 更新洞察状态
     */
    public void updateInsightStatus(String insightId, KeyInsight.InsightStatus status) {
        this.keyInsights.stream()
            .filter(i -> i.getId().equals(insightId))
            .findFirst()
            .ifPresent(i -> i.setStatus(status));
        this.lastUpdated = System.currentTimeMillis();
    }

    /**
     * 添加共识点
     */
    public void addConsensus(String point) {
        this.consensusPoints.add(point);
        this.lastUpdated = System.currentTimeMillis();
    }

    /**
     * 添加冲突点
     */
    public void addConflict(ConflictPoint conflict) {
        this.conflictPoints.add(conflict);
        this.lastUpdated = System.currentTimeMillis();
    }
}
