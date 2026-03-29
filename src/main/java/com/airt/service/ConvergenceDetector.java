package com.airt.service;

import com.airt.dto.AgentResponse;
import com.airt.model.SharedBlackboard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 讨论收敛检测器
 *
 * 检测讨论是否趋于收敛，用于提示人类决策
 */
@Slf4j
@Component
public class ConvergenceDetector {

    /**
     * 检测收敛状态
     *
     * @param history 讨论历史
     * @param blackboard 公共白板
     * @return 收敛结果
     */
    public ConvergenceResult check(List<AgentResponse> history, SharedBlackboard blackboard) {
        if (history == null || history.size() < 4 || blackboard == null) {
            return ConvergenceResult.stillActive();
        }

        // 最近 2 轮的发言
        int agentCount = estimateAgentCount(history);
        int recentHistorySize = Math.min(history.size(), agentCount * 2);
        List<AgentResponse> recent = history.subList(history.size() - recentHistorySize, history.size());

        // 1. 检查 pending questions 是否全部处理
        boolean questionsResolved = blackboard.getPendingQuestions() == null
                || blackboard.getPendingQuestions().isEmpty();

        // 2. 检查最近是否有新共识
        boolean noNewConsensus = true;
        for (AgentResponse r : recent) {
            if (r.getBlackboardUpdate() != null && r.getBlackboardUpdate().getNewConsensusPoints() != null) {
                noNewConsensus = false;
                break;
            }
        }

        // 3. 检查最近是否有新冲突
        boolean noNewConflicts = true;
        for (AgentResponse r : recent) {
            if (r.getBlackboardUpdate() != null && r.getBlackboardUpdate().getNewConflictPoints() != null
                    && !r.getBlackboardUpdate().getNewConflictPoints().isEmpty()) {
                noNewConflicts = false;
                break;
            }
        }

        // 4. 检查洞察是否全部已接受或拒绝
        boolean insightsResolved = true;
        if (blackboard.getKeyInsights() != null) {
            for (SharedBlackboard.KeyInsight insight : blackboard.getKeyInsights()) {
                if (insight.getStatus() == SharedBlackboard.KeyInsight.InsightStatus.PROPOSED
                        || insight.getStatus() == SharedBlackboard.KeyInsight.InsightStatus.PENDING_VERIFICATION) {
                    insightsResolved = false;
                    break;
                }
            }
        }

        // 综合判断
        if (questionsResolved && noNewConsensus && noNewConflicts && insightsResolved) {
            return ConvergenceResult.converged("All pending questions resolved, no new conflicts or consensus points in recent rounds, and all insights settled.");
        }

        if (questionsResolved && noNewConsensus) {
            return ConvergenceResult.nearlyConverged("Questions resolved, no new consensus. Discussion may be converging.");
        }

        if (blackboard.getConflictMap() != null && blackboard.getConflictMap().isEmpty() && questionsResolved) {
            return ConvergenceResult.nearlyConverged("No remaining conflicts or questions. Consider concluding.");
        }

        return ConvergenceResult.stillActive();
    }

    private int estimateAgentCount(List<AgentResponse> history) {
        if (history.size() <= 1) return 1;
        // 统计不同角色数
        long uniqueRoles = history.stream()
                .map(AgentResponse::getRoleName)
                .distinct()
                .count();
        return (int) Math.max(uniqueRoles, 2);
    }

    public static class ConvergenceResult {
        private final Status status;
        private final String reason;

        private ConvergenceResult(Status status, String reason) {
            this.status = status;
            this.reason = reason;
        }

        public static ConvergenceResult converged(String reason) {
            return new ConvergenceResult(Status.CONVERGED, reason);
        }

        public static ConvergenceResult nearlyConverged(String reason) {
            return new ConvergenceResult(Status.NEARLY_CONVERGED, reason);
        }

        public static ConvergenceResult stillActive() {
            return new ConvergenceResult(Status.ACTIVE, "Discussion is still active.");
        }

        public Status getStatus() { return status; }
        public String getReason() { return reason; }

        public enum Status {
            ACTIVE,            // 仍在讨论
            NEARLY_CONVERGED,  // 接近收敛
            CONVERGED          // 已收敛
        }
    }
}
