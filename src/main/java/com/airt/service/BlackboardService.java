package com.airt.service;

import com.airt.dto.AgentResponse;
import com.airt.model.SharedBlackboard;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 公共白板服务
 *
 * 管理公共白板的更新和查询
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlackboardService {

    private final ObjectMapper objectMapper;

    /**
     * 从 Agent 响应更新白板
     *
     * @param blackboard 白板
     * @param response Agent 响应
     */
    public void updateFromResponse(SharedBlackboard blackboard, AgentResponse response) {
        // 添加关键洞察
        if (response.getKeyInsights() != null) {
            for (String insight : response.getKeyInsights()) {
                SharedBlackboard.KeyInsight keyInsight = SharedBlackboard.KeyInsight.builder()
                        .id("INS-" + System.currentTimeMillis())
                        .text(insight)
                        .proponent(response.getRoleName())
                        .status(SharedBlackboard.KeyInsight.InsightStatus.PROPOSED)
                        .build();

                blackboard.addInsight(keyInsight);
            }
        }

        // 处理白板更新（主要用于记录员角色）
        if (response.getBlackboardUpdate() != null) {
            AgentResponse.BlackboardUpdate update = response.getBlackboardUpdate();

            // 更新讨论摘要
            if (update.getSummary() != null && !update.getSummary().isBlank()) {
                blackboard.setDiscussionSummary(update.getSummary());
                log.debug("Updated discussion summary: {}", update.getSummary());
            }

            // 替换共识点（使用记录员提供的完整列表）
            if (update.getNewConsensusPoints() != null) {
                blackboard.setConsensusPoints(new java.util.ArrayList<>(update.getNewConsensusPoints()));
                log.debug("Replaced consensus points with {} items", update.getNewConsensusPoints().size());
            }

            // 替换冲突点（使用记录员提供的完整列表）
            if (update.getNewConflictPoints() != null && !update.getNewConflictPoints().isEmpty()) {
                List<SharedBlackboard.ConflictPoint> conflictPoints = new ArrayList<>();
                for (String conflict : update.getNewConflictPoints()) {
                    SharedBlackboard.ConflictPoint conflictPoint = SharedBlackboard.ConflictPoint.builder()
                            .id("CONF-" + System.currentTimeMillis() + conflictPoints.size())
                            .topic(conflict)
                            .type(SharedBlackboard.ConflictPoint.ConflictType.FACTUAL_DISAGREEMENT)
                            .build();
                    conflictPoints.add(conflictPoint);
                }
                blackboard.setConflictPoints(conflictPoints);
                log.debug("Replaced conflict points with {} items", conflictPoints.size());
            } else if (update.getNewConflictPoints() != null) {
                // 空列表表示清空
                blackboard.setConflictPoints(new java.util.ArrayList<>());
                log.debug("Cleared conflict points");
            }

            // 替换待解决问题（使用记录员提供的完整列表）
            if (update.getNewPendingQuestions() != null) {
                blackboard.setPendingQuestions(new java.util.ArrayList<>(update.getNewPendingQuestions()));
                log.debug("Replaced pending questions with {} items", update.getNewPendingQuestions().size());
            }

            log.info("✓ Blackboard updated by scribe - consensus: {}, conflicts: {}, questions: {}",
                    blackboard.getConsensusPoints().size(),
                    blackboard.getConflictPoints().size(),
                    blackboard.getPendingQuestions().size());
        }

        blackboard.setLastUpdated(System.currentTimeMillis());

        log.debug("Updated blackboard for session {}, total insights: {}, consensus: {}, conflicts: {}",
                blackboard.getSessionId(),
                blackboard.getKeyInsights().size(),
                blackboard.getConsensusPoints().size(),
                blackboard.getConflictPoints().size());
    }

    /**
     * 将白板转换为 JSON
     *
     * @param blackboard 白板
     * @return JSON 字符串
     */
    public String toJson(SharedBlackboard blackboard) {
        try {
            return objectMapper.writeValueAsString(blackboard);
        } catch (JsonProcessingException e) {
            log.error("Error serializing blackboard", e);
            return "{}";
        }
    }

    /**
     * 从 JSON 解析白板
     *
     * @param json JSON 字符串
     * @return 白板对象
     */
    public SharedBlackboard fromJson(String json) {
        try {
            return objectMapper.readValue(json, SharedBlackboard.class);
        } catch (JsonProcessingException e) {
            log.error("Error deserializing blackboard", e);
            return new SharedBlackboard();
        }
    }
}
