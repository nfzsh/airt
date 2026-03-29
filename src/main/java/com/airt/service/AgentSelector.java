package com.airt.service;

import com.airt.dto.AgentResponse;
import com.airt.model.AgentInstance;
import com.airt.model.SharedBlackboard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Agent 选择器
 *
 * 支持多种调度策略：round-robin、smart（基于讨论状态智能选择）
 */
@Slf4j
@Component
public class AgentSelector {

    /**
     * 按顺序选择下一个发言者（round-robin）
     */
    public AgentInstance selectNextSpeakerInOrder(List<AgentInstance> agents, int currentIndex) {
        int nextIndex = currentIndex % agents.size();
        AgentInstance selected = agents.get(nextIndex);
        log.info("Selected speaker [{}]: {} (round-robin)", nextIndex, selected.getRoleDefinition().getDisplayName());
        return selected;
    }

    /**
     * 智能选择下一个发言者
     *
     * 策略优先级：
     * 1. 如果有 pending questions 指向某个角色，优先该角色
     * 2. 如果新出现了冲突点，优先 devil's advocate
     * 3. 如果 scribe 还没发言过本轮，优先 scribe
     * 4. 否则 round-robin
     *
     * @param agents 所有 Agent
     * @param currentIndex 当前索引
     * @param history 讨论历史
     * @param blackboard 公共白板
     * @return 选择的 Agent
     */
    public AgentInstance selectNextSpeakerSmart(List<AgentInstance> agents, int currentIndex,
                                                List<AgentResponse> history, SharedBlackboard blackboard) {
        // 1. 如果有 pending questions，找最相关的角色回答
        AgentInstance fromQuestion = findAgentForPendingQuestion(agents, blackboard);
        if (fromQuestion != null) {
            log.info("Selected speaker based on pending question: {}",
                    fromQuestion.getRoleDefinition().getDisplayName());
            return fromQuestion;
        }

        // 2. 如果有新冲突点，优先 devil's advocate
        if (hasNewConflicts(history, blackboard)) {
            AgentInstance devil = findAgentByRole(agents, "devil_advocate");
            if (devil != null && !isLastSpeaker(devil, history)) {
                log.info("Selected devil's advocate due to new conflicts");
                return devil;
            }
        }

        // 3. 如果 scribe 本轮还没发言，优先 scribe
        AgentInstance scribe = findAgentByRole(agents, "scribe");
        if (scribe != null && !hasSpokenThisRound(scribe, history, agents, blackboard)) {
            log.info("Selected scribe (hasn't spoken this round)");
            return scribe;
        }

        // 4. Fallback: round-robin
        return selectNextSpeakerInOrder(agents, currentIndex);
    }

    /**
     * 根据 pending questions 找最相关的角色
     */
    private AgentInstance findAgentForPendingQuestion(List<AgentInstance> agents, SharedBlackboard blackboard) {
        if (blackboard == null || blackboard.getPendingQuestions() == null) return null;
        List<String> questions = blackboard.getPendingQuestions();
        if (questions.isEmpty()) return null;

        // 关键词匹配：问题中的关键词 -> 角色映射
        for (String question : questions) {
            String lower = question.toLowerCase();

            // 技术问题 -> engineer
            if (containsAny(lower, "技术", "架构", "性能", "实现", "技术方案", "tech", "architecture", "performance")) {
                AgentInstance eng = findAgentByRoleInOrder(agents, List.of("backend_engineer", "frontend_engineer"));
                if (eng != null) return eng;
            }

            // 产品/用户问题 -> PM
            if (containsAny(lower, "用户", "需求", "产品", "体验", "user", "product", "ux")) {
                AgentInstance pm = findAgentByRole(agents, "product_manager");
                if (pm != null) return pm;
            }

            // 业务/ROI 问题 -> business owner
            if (containsAny(lower, "成本", "预算", "roi", "业务", "市场", "cost", "budget", "market")) {
                AgentInstance biz = findAgentByRole(agents, "business_owner");
                if (biz != null) return biz;
            }
        }

        return null;
    }

    /**
     * 检查是否有新的冲突点（最近一轮新增的）
     */
    private boolean hasNewConflicts(List<AgentResponse> history, SharedBlackboard blackboard) {
        if (blackboard == null || blackboard.getConflictMap() == null) return false;
        return !blackboard.getConflictMap().isEmpty();
    }

    /**
     * 检查 Agent 本轮是否已发言
     */
    private boolean hasSpokenThisRound(AgentInstance agent, List<AgentResponse> history,
                                       List<AgentInstance> agents, SharedBlackboard blackboard) {
        if (history.isEmpty()) return false;
        // 简单判断：最近 agents.size() 条发言中是否有该角色
        int checkRange = Math.min(history.size(), agents.size());
        for (int i = history.size() - checkRange; i < history.size(); i++) {
            if (history.get(i).getRoleName().equals(agent.getRoleDefinition().getRoleId())) {
                return true;
            }
        }
        return false;
    }

    private AgentInstance findAgentByRole(List<AgentInstance> agents, String roleId) {
        return agents.stream()
                .filter(a -> a.getRoleDefinition().getRoleId().equals(roleId))
                .findFirst()
                .orElse(null);
    }

    private AgentInstance findAgentByRoleInOrder(List<AgentInstance> agents, List<String> roleIds) {
        for (String roleId : roleIds) {
            AgentInstance agent = findAgentByRole(agents, roleId);
            if (agent != null) return agent;
        }
        return null;
    }

    private boolean isLastSpeaker(AgentInstance agent, List<AgentResponse> history) {
        if (history.isEmpty()) return false;
        return history.get(history.size() - 1).getRoleName().equals(agent.getRoleDefinition().getRoleId());
    }

    private boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }
}
