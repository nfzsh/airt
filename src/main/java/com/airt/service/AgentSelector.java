package com.airt.service;

import com.airt.dto.AgentResponse;
import com.airt.model.AgentInstance;
import com.airt.model.SharedBlackboard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Agent 选择器
 *
 * 负责选择下一个发言的 Agent
 */
@Slf4j
@Component
public class AgentSelector {

    /**
     * 按顺序选择下一个发言者
     *
     * @param agents 所有 Agent
     * @param currentIndex 当前发言人索引
     * @return 选择的 Agent
     */
    public AgentInstance selectNextSpeakerInOrder(List<AgentInstance> agents, int currentIndex) {
        // 按顺序循环选择
        int nextIndex = currentIndex % agents.size();
        AgentInstance selected = agents.get(nextIndex);

        log.info("Selected speaker [{}]: {} (total agents: {})",
                nextIndex,
                selected.getRoleDefinition().getDisplayName(),
                agents.size());

        return selected;
    }
}
