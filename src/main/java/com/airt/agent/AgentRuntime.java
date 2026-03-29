package com.airt.agent;

import com.airt.config.LlmProperties;
import com.airt.dto.AgentResponse;
import com.airt.mcp.MCPService;
import com.airt.dto.AgentResponse.MCPToolCall;
import com.airt.mcp.MCPToolExecutionResult;
import com.airt.model.*;
import com.airt.service.ConversationMemoryService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Agent 运行时
 *
 * 负责执行 Agent 的思考过程，支持多轮对话记忆
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentRuntime {

    private final MCPService mcpService;
    private final LlmProperties llmProperties;
    private final ConversationMemoryService memoryService;

    // 模型缓存（线程安全）
    private final Map<String, ChatLanguageModel> modelCache = new ConcurrentHashMap<>();
    private final Map<String, StreamingChatLanguageModel> streamingModelCache = new ConcurrentHashMap<>();

    /**
     * 执行 Agent
     */
    public AgentResponse execute(AgentInstance agent, SessionContext context, String previousSpeaker) {
        return execute(agent, context, previousSpeaker, null);
    }

    /**
     * 执行 Agent（支持定向提问，带记忆）
     */
    public AgentResponse execute(AgentInstance agent, SessionContext context, String previousSpeaker, String directQuestion) {
        long startTime = System.currentTimeMillis();

        try {
            // 1. 构建系统提示（动态生成，不存入记忆）
            SystemMessage systemMessage = buildSystemMessage(agent, context, previousSpeaker);

            // 2. 构建用户消息（可能包含 whisper 和定向提问）
            UserMessage userMessage = buildUserMessage(agent, context, previousSpeaker, directQuestion);

            // 3. 获取或创建 Agent 的对话记忆
            MessageWindowChatMemory memory = memoryService.getOrCreateMemory(context.getSessionId(), agent);

            // 4. 构建完整消息列表：SystemMessage + 记忆 + 当前 UserMessage
            List<ChatMessage> messages = memoryService.buildMessageList(systemMessage, memory, userMessage);

            log.debug("Executing agent {} with {} messages (memory: {})",
                    agent.getRoleDefinition().getRoleId(), messages.size(), memory.messages().size());

            // 5. 获取聊天模型并执行（带工具调用循环）
            ChatLanguageModel model = getChatModel(agent.getModel());
            String responseText = executeWithToolLoop(model, messages, agent, context.getCurrentRound(), startTime);

            // 6. 解析响应
            AgentResponse agentResponse = parseResponse(agent, responseText, startTime, context.getCurrentRound());

            // 7. 将本轮对话保存到记忆（UserMessage + AiMessage）
            memoryService.saveExchange(context.getSessionId(), agent, userMessage,
                    AiMessage.from(responseText));

            return agentResponse;

        } catch (Exception e) {
            log.error("Error executing agent {}: {}", agent.getInstanceId(), e.getMessage(), e);
            return AgentResponse.builder()
                    .agentInstanceId(agent.getInstanceId())
                    .roleName(agent.getRoleDefinition().getRoleId())
                    .roleDisplayName(agent.getRoleDefinition().getDisplayName())
                    .publicResponse("I apologize, but I encountered an error: " + e.getMessage())
                    .timestamp(System.currentTimeMillis())
                    .build();
        }
    }

    /**
     * 构建系统提示消息对象（不存入记忆）
     */
    private SystemMessage buildSystemMessage(AgentInstance agent, SessionContext context, String previousSpeaker) {
        String prompt = buildSystemPrompt(agent, context, previousSpeaker);
        return SystemMessage.from(prompt);
    }

    /**
     * 构建系统提示文本
     */
    private String buildSystemPrompt(AgentInstance agent, SessionContext context, String previousSpeaker) {
        RoleDefinition role = agent.getRoleDefinition();
        PromptTemplate template = agent.getPromptTemplate();

        if (template != null) {
            Map<String, Object> vars = new HashMap<>();
            vars.put("ROLE_NAME", role.getDisplayName());
            vars.put("CORE_GOAL", String.join(", ", role.getCoreResponsibility()));
            vars.put("COGNITIVE_STYLE", String.join(", ", role.getCognitiveStyle()));
            vars.put("TOPIC", context.getTopic());
            vars.put("BLACKBOARD_JSON", context.getBlackboardJson());
            vars.put("PREV_SPEAKER", previousSpeaker != null ? previousSpeaker : "None");
            vars.put("HISTORY_SUMMARY", context.getHistorySummary());
            vars.put("CURRENT_ROUND", String.valueOf(context.getCurrentRound()));
            return template.render(vars);
        }

        return String.format(
                "你是【%s】。\n" +
                "你的认知风格：%s\n" +
                "你的核心职责：%s\n" +
                "你必须严格从【%s】的视角发言，只在你专业领域内给出判断，不要越界到其他角色的专业范围。\n\n" +
                "[当前讨论 - 第 %d 轮]\n" +
                "主题：%s\n\n" +
                "[之前的发言摘要]\n" +
                "%s\n\n" +
                "[公共白板]\n" +
                "%s\n\n" +
                "[上一位发言者]\n" +
                "%s\n\n" +
                "[约束]\n" +
                "- 严格保持角色视角，不要扮演其他角色\n" +
                "- 你可以看到下方对话历史中的近期对话记录\n" +
                "- 不要重复白板中已达成共识的观点\n" +
                "- 回应之前发言者的观点，不要自说自话\n" +
                "- 需要时使用工具验证事实\n" +
                "- 用中文发言，给出清晰、结构化的回答",
                role.getDisplayName(),
                String.join(", ", role.getCoreResponsibility()),
                String.join(", ", role.getCognitiveStyle()),
                context.getCurrentRound(),
                context.getTopic(),
                context.getHistorySummary(),
                context.getBlackboardJson(),
                previousSpeaker != null ? previousSpeaker : "None (you are the first speaker)"
        );
    }

    /**
     * 构建用户消息
     */
    private UserMessage buildUserMessage(AgentInstance agent, SessionContext context, String previousSpeaker, String directQuestion) {
        StringBuilder sb = new StringBuilder();
        RoleDefinition role = agent.getRoleDefinition();
        String displayName = role.getDisplayName();

        // 消费 whisper（私信）
        String whispers = agent.drainWhispers();
        if (whispers != null) {
            sb.append("【私信】\n").append(whispers).append("\n\n");
        }

        // 定向提问
        if (directQuestion != null && !directQuestion.isBlank()) {
            sb.append("【人类定向提问】\n").append(directQuestion).append("\n\n")
                    .append(String.format("请以%s的身份和视角，针对这个问题给出你的专业分析。", displayName));
        } else if (previousSpeaker == null) {
            sb.append(String.format("""
                    请针对讨论主题："%s"发表你的观点。

                    重要提醒：你是【%s】，你的认知风格是【%s】。
                    你必须严格从%s的视角出发，只在你专业领域内发言。
                    不要扮演其他角色，不要给出泛泛而谈的回答。

                    作为%s，你的立场和关键关切是什么？
                    """,
                    context.getTopic(),
                    displayName,
                    String.join("、", role.getCognitiveStyle()),
                    displayName,
                    displayName));
        } else {
            sb.append(String.format("""
                    上一位发言者是【%s】。

                    请作为【%s】回应ta的观点。你可以：
                    - 从%s的视角提供支持性证据
                    - 从%s的专业角度质疑ta的假设
                    - 提出你视角下的补充观点
                    - 指出你专业领域内的问题或风险

                    重要：严格保持【%s】的视角，不要越界到其他角色的专业领域。
                    """,
                    previousSpeaker,
                    displayName,
                    displayName,
                    displayName,
                    displayName));
        }

        return UserMessage.from(sb.toString());
    }

    // ========== 响应解析（保持原有逻辑） ==========

    private AgentResponse parseResponse(AgentInstance agent, String rawResponse, long startTime, int currentRound) {
        String thinking = "";
        String publicResponse = rawResponse;
        List<MCPToolCall> toolCalls = new ArrayList<>();

        Pattern thinkingPattern = Pattern.compile("<thinking>(.*?)</thinking>", Pattern.DOTALL);
        Matcher thinkingMatcher = thinkingPattern.matcher(rawResponse);
        if (thinkingMatcher.find()) {
            thinking = thinkingMatcher.group(1).trim();
            publicResponse = rawResponse.replaceFirst("<thinking>.*?</thinking>", "").trim();
        }

        Pattern responsePattern = Pattern.compile("<response>(.*?)</response>", Pattern.DOTALL);
        Matcher responseMatcher = responsePattern.matcher(publicResponse);
        if (responseMatcher.find()) {
            publicResponse = responseMatcher.group(1).trim();
        } else {
            publicResponse = publicResponse.replaceAll("<[^>]+>", "").trim();
        }

        Pattern toolPattern = Pattern.compile("CALL_MCP:\\s*(\\w+)\\((.*?)\\)");
        Matcher toolMatcher = toolPattern.matcher(publicResponse);
        while (toolMatcher.find()) {
            String toolName = toolMatcher.group(1);
            String params = toolMatcher.group(2);
            MCPToolExecutionResult result = mcpService.execute(
                    agent.getRoleDefinition().getRoleId(), toolName, parseToolParams(params));
            toolCalls.add(MCPToolCall.builder()
                    .toolName(toolName).input(params)
                    .output(result.isSuccess() ? result.getResult() : result.getError())
                    .duration(result.getDuration()).success(result.isSuccess()).build());
        }
        publicResponse = publicResponse.replaceAll("CALL_MCP:\\s*\\w+\\(.*?\\)", "").trim();

        List<String> keyInsights = extractKeyInsights(publicResponse);

        AgentResponse.BlackboardUpdate blackboardUpdate = null;
        if ("scribe".equals(agent.getRoleDefinition().getRoleId())) {
            blackboardUpdate = parseBlackboardToolCall(publicResponse);
        }

        return AgentResponse.builder()
                .agentInstanceId(agent.getInstanceId())
                .roleName(agent.getRoleDefinition().getRoleId())
                .roleDisplayName(agent.getRoleDefinition().getDisplayName())
                .round(currentRound)
                .thinkingProcess(thinking)
                .publicResponse(publicResponse)
                .keyInsights(keyInsights)
                .toolCalls(toolCalls)
                .timestamp(System.currentTimeMillis())
                .blackboardUpdate(blackboardUpdate)
                .metadata(Map.of("model", agent.getModel(), "responseTime", System.currentTimeMillis() - startTime))
                .build();
    }

    private AgentResponse.BlackboardUpdate parseBlackboardToolCall(String response) {
        Pattern toolPattern = Pattern.compile("UPDATE_BLACKBOARD\\s*\\(\\s*(\\{[\\s\\S]*\\})\\s*\\)", Pattern.DOTALL);
        Matcher matcher = toolPattern.matcher(response);
        if (!matcher.find()) return null;

        String jsonContent = matcher.group(1);
        try {
            ObjectMapper mapper = new ObjectMapper();
            TypeReference<Map<String, Object>> typeRef = new TypeReference<>() {};
            Map<String, Object> data = mapper.readValue(jsonContent, typeRef);

            @SuppressWarnings("unchecked")
            List<String> consensus = (List<String>) data.getOrDefault("consensus", new ArrayList<>());
            @SuppressWarnings("unchecked")
            List<String> conflicts = (List<String>) data.getOrDefault("conflicts", new ArrayList<>());
            @SuppressWarnings("unchecked")
            List<String> questions = (List<String>) data.getOrDefault("questions", new ArrayList<>());
            String summary = (String) data.get("summary");

            return AgentResponse.BlackboardUpdate.builder()
                    .newConsensusPoints(consensus).newConflictPoints(conflicts)
                    .newPendingQuestions(questions).summary(summary).build();
        } catch (Exception e) {
            log.error("Failed to parse blackboard tool call", e);
            return null;
        }
    }

    private Map<String, Object> parseToolParams(String params) {
        Map<String, Object> result = new HashMap<>();
        result.put("query", params);
        return result;
    }

    private List<String> extractKeyInsights(String response) {
        List<String> insights = new ArrayList<>();
        Pattern pattern = Pattern.compile("(?:关键观点|Key point|Insight)[:：](.*?)(?:\\n|$)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(response);
        while (matcher.find()) {
            insights.add(matcher.group(1).trim());
        }
        return insights;
    }

    // ========== ReAct 工具调用循环 ==========

    private static final int MAX_TOOL_ROUNDS = 5;
    private static final Pattern TOOL_CALL_PATTERN = Pattern.compile("CALL_MCP:\\s*(\\w+)\\((.*?)\\)", Pattern.DOTALL);

    /**
     * 执行 LLM 调用，支持多轮工具调用循环（ReAct 模式）
     * 最多循环 MAX_TOOL_ROUNDS 轮
     */
    private String executeWithToolLoop(ChatLanguageModel model, List<ChatMessage> messages,
                                       AgentInstance agent, int currentRound, long startTime) {
        String roleId = agent.getRoleDefinition().getRoleId();
        StringBuilder accumulatedResponse = new StringBuilder();

        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            // 调用 LLM
            String responseText = model.generate(messages).content().text();

            // 检测是否包含工具调用
            List<ToolCallInfo> toolCalls = extractToolCalls(responseText);
            if (toolCalls.isEmpty()) {
                // 无工具调用，返回最终响应（累积之前的 + 本次）
                if (accumulatedResponse.length() > 0) {
                    accumulatedResponse.append("\n\n");
                }
                accumulatedResponse.append(responseText);
                return accumulatedResponse.toString();
            }

            log.info("Agent {} triggered {} tool call(s) in ReAct round {}/{}",
                    roleId, toolCalls.size(), round + 1, MAX_TOOL_ROUNDS);

            // 累积响应
            if (accumulatedResponse.length() > 0) {
                accumulatedResponse.append("\n\n");
            }
            accumulatedResponse.append(responseText);

            // 将 AI 响应添加到消息列表
            messages.add(AiMessage.from(responseText));

            // 执行所有工具调用，收集结果
            StringBuilder toolResults = new StringBuilder();
            for (ToolCallInfo call : toolCalls) {
                MCPToolExecutionResult result = mcpService.execute(roleId, call.name, parseToolParams(call.params));
                String output = result.isSuccess() ? result.getResult() : "Error: " + result.getError();
                toolResults.append(String.format("[Tool: %s] %s\n", call.name, output));
                log.debug("Tool {} executed: success={}, duration={}ms", call.name, result.isSuccess(), result.getDuration());
            }

            // 将工具结果作为 UserMessage 追加到消息列表
            messages.add(UserMessage.from(
                    "[Tool execution results]\n" + toolResults + "\nBased on the tool results above, continue your analysis. If you need more information, call another tool."));
        }

        log.warn("Agent {} reached max tool call rounds ({})", roleId, MAX_TOOL_ROUNDS);
        return accumulatedResponse.toString();
    }

    /**
     * 从 LLM 响应中提取 CALL_MCP 工具调用
     */
    private List<ToolCallInfo> extractToolCalls(String responseText) {
        List<ToolCallInfo> calls = new ArrayList<>();
        Matcher matcher = TOOL_CALL_PATTERN.matcher(responseText);
        while (matcher.find()) {
            calls.add(new ToolCallInfo(matcher.group(1), matcher.group(2)));
        }
        return calls;
    }

    /**
     * 工具调用信息（内部辅助类）
     */
    private static class ToolCallInfo {
        final String name;
        final String params;
        ToolCallInfo(String name, String params) {
            this.name = name;
            this.params = params;
        }
    }

    // ========== 模型管理 ==========

    private ChatLanguageModel getChatModel(String modelName) {
        return modelCache.computeIfAbsent(modelName, this::createChatModel);
    }

    private ChatLanguageModel createChatModel(String modelName) {
        LlmProperties.ProviderType providerType = llmProperties.getProviderType(modelName);
        switch (providerType) {
            case ANTHROPIC: return createAnthropicModel(modelName);
            default: return createOpenAIModel(modelName);
        }
    }

    private ChatLanguageModel createOpenAIModel(String modelName) {
        String apiKey = llmProperties.getApiKey(modelName);
        if (apiKey == null || apiKey.trim().isEmpty() || apiKey.equals("your-api-key-here")) {
            throw new IllegalStateException("OpenAI API key is not configured");
        }
        return OpenAiChatModel.builder()
                .apiKey(apiKey).baseUrl(llmProperties.getBaseUrl(modelName))
                .modelName(modelName).temperature(llmProperties.getTemperature(modelName))
                .maxTokens(llmProperties.getMaxTokens(modelName))
                .timeout(llmProperties.getOpenai().getTimeoutAsDuration()).build();
    }

    private ChatLanguageModel createAnthropicModel(String modelName) {
        String apiKey = llmProperties.getApiKey(modelName);
        if (apiKey == null || apiKey.trim().isEmpty() || apiKey.equals("your-api-key-here")) {
            throw new IllegalStateException("Anthropic API key is not configured");
        }
        return AnthropicChatModel.builder()
                .apiKey(apiKey).baseUrl(llmProperties.getBaseUrl(modelName))
                .modelName(modelName).temperature(llmProperties.getTemperature(modelName))
                .maxTokens(llmProperties.getMaxTokens(modelName))
                .timeout(llmProperties.getAnthropic().getTimeoutAsDuration()).build();
    }

    // ========== 流式模型 ==========

    private StreamingChatLanguageModel getStreamingChatModel(String modelName) {
        return streamingModelCache.computeIfAbsent(modelName, this::createStreamingChatModel);
    }

    private StreamingChatLanguageModel createStreamingChatModel(String modelName) {
        LlmProperties.ProviderType providerType = llmProperties.getProviderType(modelName);
        switch (providerType) {
            case ANTHROPIC: return createAnthropicStreamingModel(modelName);
            default: return createOpenAIStreamingModel(modelName);
        }
    }

    private StreamingChatLanguageModel createOpenAIStreamingModel(String modelName) {
        String apiKey = llmProperties.getApiKey(modelName);
        if (apiKey == null || apiKey.trim().isEmpty() || apiKey.equals("your-api-key-here")) {
            throw new IllegalStateException("OpenAI API key is not configured");
        }
        return OpenAiStreamingChatModel.builder()
                .apiKey(apiKey).baseUrl(llmProperties.getBaseUrl(modelName))
                .modelName(modelName).temperature(llmProperties.getTemperature(modelName))
                .timeout(llmProperties.getOpenai().getTimeoutAsDuration()).build();
    }

    private StreamingChatLanguageModel createAnthropicStreamingModel(String modelName) {
        String apiKey = llmProperties.getApiKey(modelName);
        if (apiKey == null || apiKey.trim().isEmpty() || apiKey.equals("your-api-key-here")) {
            throw new IllegalStateException("Anthropic API key is not configured");
        }
        return AnthropicStreamingChatModel.builder()
                .apiKey(apiKey).baseUrl(llmProperties.getBaseUrl(modelName))
                .modelName(modelName).temperature(llmProperties.getTemperature(modelName))
                .timeout(llmProperties.getAnthropic().getTimeoutAsDuration()).build();
    }

    // ========== 流式执行 ==========

    /**
     * 流式执行 Agent（带记忆）
     */
    public Flux<String> streamExecute(AgentInstance agent, SessionContext context, String previousSpeaker,
                                     java.util.function.Consumer<AgentResponse> responseCallback) {
        return Flux.create(sink -> {
            try {
                SystemMessage systemMessage = buildSystemMessage(agent, context, previousSpeaker);
                UserMessage userMessage = buildUserMessage(agent, context, previousSpeaker, null);
                MessageWindowChatMemory memory = memoryService.getOrCreateMemory(context.getSessionId(), agent);
                List<ChatMessage> messages = memoryService.buildMessageList(systemMessage, memory, userMessage);
                StreamingChatLanguageModel model = getStreamingChatModel(agent.getModel());
                ObjectMapper objectMapper = new ObjectMapper();

                // 发送开始标记
                Map<String, Object> startData = new HashMap<>();
                startData.put("type", "start");
                startData.put("agentId", agent.getInstanceId());
                startData.put("roleName", agent.getRoleDefinition().getRoleId());
                startData.put("roleDisplayName", agent.getRoleDefinition().getDisplayName());
                startData.put("round", context.getCurrentRound());
                sink.next(objectMapper.writeValueAsString(startData));

                log.info("Starting stream for agent {} (memory: {} messages)",
                        agent.getInstanceId(), memory.messages().size());

                model.generate(messages, new StreamingResponseHandler<>() {
                    private final StringBuilder fullResponse = new StringBuilder();
                    private final long startTime = System.currentTimeMillis();

                    @Override
                    public void onNext(String token) {
                        try {
                            Map<String, Object> tokenData = new HashMap<>();
                            tokenData.put("type", "token");
                            tokenData.put("content", token);
                            sink.next(objectMapper.writeValueAsString(tokenData));
                            fullResponse.append(token);
                        } catch (JsonProcessingException e) {
                            log.error("Error serializing token", e);
                        }
                    }

                    @Override
                    public void onComplete(Response<AiMessage> response) {
                        try {
                            String responseText = fullResponse.toString();
                            AgentResponse agentResponse = parseResponse(agent, responseText, startTime, context.getCurrentRound());

                            // 保存到记忆
                            memoryService.saveExchange(context.getSessionId(), agent, userMessage,
                                    AiMessage.from(responseText));

                            if (responseCallback != null) {
                                responseCallback.accept(agentResponse);
                            }

                            Map<String, Object> completeData = new HashMap<>();
                            completeData.put("type", "complete");
                            completeData.put("response", agentResponse);
                            sink.next(objectMapper.writeValueAsString(completeData));
                            sink.complete();
                        } catch (Exception e) {
                            log.error("Error completing stream for agent {}", agent.getInstanceId(), e);
                            sink.error(e);
                        }
                    }

                    @Override
                    public void onError(Throwable error) {
                        log.error("Streaming error for agent {}", agent.getInstanceId(), error);
                        try {
                            Map<String, Object> errorData = new HashMap<>();
                            errorData.put("type", "error");
                            errorData.put("error", error.getMessage());
                            sink.next(objectMapper.writeValueAsString(errorData));
                        } catch (JsonProcessingException e) {
                            log.error("Error serializing error", e);
                        }
                        sink.error(error);
                    }
                });
            } catch (Exception e) {
                log.error("Error starting stream for agent {}", agent.getInstanceId(), e);
                sink.error(e);
            }
        });
    }
}
