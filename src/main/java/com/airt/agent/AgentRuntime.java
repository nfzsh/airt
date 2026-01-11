package com.airt.agent;

import com.airt.config.LlmProperties;
import com.airt.dto.AgentResponse;
import com.airt.mcp.MCPService;
import com.airt.dto.AgentResponse.MCPToolCall;
import com.airt.mcp.MCPToolExecutionResult;
import com.airt.model.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.UserMessage;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Agent 运行时
 *
 * 负责执行 Agent 的思考过程
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentRuntime {

    private final MCPService mcpService;
    private final LlmProperties llmProperties;

    // 模型缓存
    private final Map<String, ChatLanguageModel> modelCache = new HashMap<>();
    private final Map<String, StreamingChatLanguageModel> streamingModelCache = new HashMap<>();

    /**
     * 执行 Agent
     *
     * @param agent Agent 实例
     * @param context 会话上下文
     * @param previousSpeaker 前一位发言者
     * @return Agent 响应
     */
    public AgentResponse execute(AgentInstance agent, SessionContext context, String previousSpeaker) {
        long startTime = System.currentTimeMillis();

        try {
            // 1. 构建系统提示
            String systemPrompt = buildSystemPrompt(agent, context, previousSpeaker);

            // 2. 构建用户消息
            String userMessage = buildUserMessage(agent, context, previousSpeaker);

            // 3. 获取聊天模型
            ChatLanguageModel model = getChatModel(agent.getModel());

            // 4. 执行 LLM 调用
            List<ChatMessage> messages = Arrays.asList(
                    SystemMessage.from(systemPrompt),
                    UserMessage.from(userMessage)
            );
            Response<AiMessage> response = model.generate(messages);
            String responseText = response.content().text();

            // 5. 解析响应
            return parseResponse(agent, responseText, startTime, context.getCurrentRound());

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
     * 构建系统提示
     */
    private String buildSystemPrompt(AgentInstance agent, SessionContext context, String previousSpeaker) {
        RoleDefinition role = agent.getRoleDefinition();
        PromptTemplate template = agent.getPromptTemplate();

        if (template != null) {
            // 使用模板渲染
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

        // 默认系统提示
        return String.format(
                "You are【%s】.\n" +
                "Your core responsibilities are: %s\n" +
                "Your cognitive style: %s\n\n" +
                "[Current Discussion - Round %d]\n" +
                "Topic: %s\n\n" +
                "[Previous Discussion]\n" +
                "%s\n\n" +
                "[Shared Blackboard]\n" +
                "%s\n\n" +
                "[Previous Speaker]\n" +
                "%s\n\n" +
                "[Constraints]\n" +
                "- Do not repeat points already accepted on the blackboard\n" +
                "- Build upon or respond to points made by previous speakers\n" +
                "- Use available tools to verify facts when needed\n" +
                "- Provide clear, structured responses",
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
    private String buildUserMessage(AgentInstance agent, SessionContext context, String previousSpeaker) {
        if (previousSpeaker == null) {
            // 第一轮发言
            return String.format("""
                    Please provide your initial perspective on the topic: "%s"

                    As the %s, what is your stance and key concerns?
                    """, context.getTopic(), agent.getRoleDefinition().getDisplayName());
        }

        return String.format("""
                The previous speaker was %s.

                Please respond to their points. You may:
                - Support their arguments with additional evidence
                - Challenge their assumptions
                - Provide alternative perspectives
                - Ask clarifying questions

                Keep your response focused and constructive.
                """, previousSpeaker);
    }

    /**
     * 解析 LLM 响应
     */
    private AgentResponse parseResponse(AgentInstance agent, String rawResponse, long startTime, int currentRound) {
        // 尝试解析结构化响应
        // 格式: <thinking>...</thinking><response>...</response>

        String thinking = "";
        String publicResponse = rawResponse;
        List<MCPToolCall> toolCalls = new ArrayList<>();

        // 提取 thinking 部分
        Pattern thinkingPattern = Pattern.compile("<thinking>(.*?)</thinking>", Pattern.DOTALL);
        Matcher thinkingMatcher = thinkingPattern.matcher(rawResponse);
        if (thinkingMatcher.find()) {
            thinking = thinkingMatcher.group(1).trim();
            // 移除 thinking 标签及其内容
            publicResponse = rawResponse.replaceFirst("<thinking>.*?</thinking>", "").trim();
        }

        // 提取 response 部分（如果有 <response> 标签）
        Pattern responsePattern = Pattern.compile("<response>(.*?)</response>", Pattern.DOTALL);
        Matcher responseMatcher = responsePattern.matcher(publicResponse);
        if (responseMatcher.find()) {
            publicResponse = responseMatcher.group(1).trim();
        } else {
            // 如果没有 <response> 标签，移除任何剩余的 XML 标签
            publicResponse = publicResponse.replaceAll("<[^>]+>", "").trim();
        }

        // 提取工具调用
        Pattern toolPattern = Pattern.compile("CALL_MCP:\\s*(\\w+)\\((.*?)\\)");
        Matcher toolMatcher = toolPattern.matcher(publicResponse);
        while (toolMatcher.find()) {
            String toolName = toolMatcher.group(1);
            String params = toolMatcher.group(2);

            // 执行工具调用
            MCPToolExecutionResult result = mcpService.execute(
                    agent.getRoleDefinition().getRoleId(),
                    toolName,
                    parseToolParams(params)
            );

            toolCalls.add(MCPToolCall.builder()
                    .toolName(toolName)
                    .input(params)
                    .output(result.isSuccess() ? result.getResult() : result.getError())
                    .duration(result.getDuration())
                    .success(result.isSuccess())
                    .build());
        }

        // 移除工具调用标记
        publicResponse = publicResponse.replaceAll("CALL_MCP:\\s*\\w+\\(.*?\\)", "").trim();

        // 提取关键洞察
        List<String> keyInsights = extractKeyInsights(publicResponse);

        // 解析白板更新工具调用（针对记录员角色）
        AgentResponse.BlackboardUpdate blackboardUpdate = null;
        if ("scribe".equals(agent.getRoleDefinition().getRoleId())) {
            log.info("Scribe detected, parsing blackboard update from response: {}", publicResponse);
            blackboardUpdate = parseBlackboardToolCall(publicResponse);
            if (blackboardUpdate != null) {
                log.info("✓ Scribe updated blackboard via tool call - consensus: {}, conflicts: {}, questions: {}",
                        blackboardUpdate.getNewConsensusPoints().size(),
                        blackboardUpdate.getNewConflictPoints().size(),
                        blackboardUpdate.getNewPendingQuestions().size());
            } else {
                log.warn("✗ Failed to parse blackboard tool call from scribe's response");
            }
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
                .metadata(Map.of(
                        "model", agent.getModel(),
                        "responseTime", System.currentTimeMillis() - startTime
                ))
                .build();
    }

    /**
     * 解析记录员的白板更新工具调用
     * 支持格式：UPDATE_BLACKBOARD({...})
     */
    private AgentResponse.BlackboardUpdate parseBlackboardToolCall(String response) {
        log.info("Attempting to parse UPDATE_BLACKBOARD tool call...");

        // 查找 UPDATE_BLACKBOARD 工具调用
        // 使用贪婪匹配确保捕获完整的 JSON 对象（包括嵌套的花括号）
        Pattern toolPattern = Pattern.compile("UPDATE_BLACKBOARD\\s*\\(\\s*(\\{[\\s\\S]*\\})\\s*\\)", Pattern.DOTALL);
        Matcher matcher = toolPattern.matcher(response);

        if (!matcher.find()) {
            log.warn("UPDATE_BLACKBOARD pattern not found in response");
            return null;
        }

        String jsonContent = matcher.group(1);
        log.info("Found UPDATE_BLACKBOARD call, JSON content: {}", jsonContent);

        try {
            // 使用 Jackson 解析 JSON
            ObjectMapper mapper = new ObjectMapper();
            TypeReference<Map<String, Object>> typeRef = new TypeReference<>() {
            };
            Map<String, Object> data = mapper.readValue(jsonContent, typeRef);

            log.info("Successfully parsed JSON: {}", data);

            // 转换为 BlackboardUpdate 对象
            @SuppressWarnings("unchecked")
            List<String> consensus = (List<String>) data.getOrDefault("consensus", new ArrayList<>());
            @SuppressWarnings("unchecked")
            List<String> conflicts = (List<String>) data.getOrDefault("conflicts", new ArrayList<>());
            @SuppressWarnings("unchecked")
            List<String> questions = (List<String>) data.getOrDefault("questions", new ArrayList<>());
            String summary = (String) data.get("summary");

            log.info("Extracted fields - consensus: {}, conflicts: {}, questions: {}, summary: {}",
                    consensus, conflicts, questions, summary);

            return AgentResponse.BlackboardUpdate.builder()
                    .newConsensusPoints(consensus)
                    .newConflictPoints(conflicts)
                    .newPendingQuestions(questions)
                    .summary(summary)
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse blackboard tool call, JSON content: {}", jsonContent, e);
            return null;
        }
    }

    /**
     * 解析记录员的白板更新
     * 从文本中提取结构化的白板更新信息
     */
    private AgentResponse.BlackboardUpdate parseBlackboardUpdate(String response) {
        List<String> newConsensusPoints = new ArrayList<>();
        List<String> newConflictPoints = new ArrayList<>();
        List<String> newPendingQuestions = new ArrayList<>();

        // 按段落处理，找到以"待澄清问题："等开头的段落
        String[] paragraphs = response.split("\\n\\s*\\n");

        for (String paragraph : paragraphs) {
            // 跳过"当前状态"、"新增记录"等非白板数据段落
            if (paragraph.matches("(?s)^(?:当前状态|新增记录|白板摘要|更新摘要|讨论进展)[:：].*")) {
                continue;
            }

            // 提取共识点
            if (paragraph.matches("(?s).*(?:共识|已达成共识)[:：].*")) {
                extractItemsFromParagraph(paragraph, "(?:共识|已达成共识)[:：]", newConsensusPoints);
            }

            // 提取冲突点/争议点
            if (paragraph.matches("(?s).*(?:分歧|争议|冲突点|争议点)[:：].*")) {
                extractItemsFromParagraph(paragraph, "(?:分歧|争议|冲突点|争议点)[:：]", newConflictPoints);
            }

            // 提取待解决问题（明确匹配，避免误匹配）
            if (paragraph.matches("(?s).*(?:待澄清问题|待解决问题|待验证陈述)[:：].*")) {
                extractItemsFromParagraph(paragraph, "(?:待澄清问题|待解决问题|待验证陈述)[:：]", newPendingQuestions);
            }
        }

        // 如果没有提取到任何信息，返回 null
        if (newConsensusPoints.isEmpty() && newConflictPoints.isEmpty() && newPendingQuestions.isEmpty()) {
            return null;
        }

        log.debug("Parsed blackboard update - Consensus: {}, Conflicts: {}, Questions: {}",
                newConsensusPoints, newConflictPoints, newPendingQuestions);

        return AgentResponse.BlackboardUpdate.builder()
                .newConsensusPoints(newConsensusPoints)
                .newConflictPoints(newConflictPoints)
                .newPendingQuestions(newPendingQuestions)
                .summary(response.substring(0, Math.min(200, response.length())))
                .build();
    }

    /**
     * 从段落中提取列表项
     */
    private void extractItemsFromParagraph(String paragraph, String keywordPattern, List<String> results) {
        // 移除关键词和冒号
        String content = paragraph.replaceFirst(keywordPattern + "[:：]?\\s*", "");

        log.debug("Extracting items from paragraph with pattern '{}': {}", keywordPattern, content);

        // 分割列表项（支持：- 开头、数字编号、分号分隔）
        String[] items = content.split("(?m)^\\s*[-•]\\s*|\\d+[.、]\\s*|[;；]\\s*");

        for (String item : items) {
            item = item.trim();

            // 跳过空项和无意义的项
            if (!item.isEmpty() &&
                !item.matches("^(暂无|无|没有|没有明确|没有达成|待记录|待补充|更多.*|需要更多).*") &&
                item.length() > 3 &&
                !item.matches("^(需要|需|建议).*") &&  // 避免匹配"建议XXX"这样的开头
                item.matches(".*[？?！!.].*")) {  // 必须包含问号或感叹号等结束符号
                // 移除 Markdown 加粗标记
                item = item.replaceAll("\\*\\*", "");
                log.debug("Extracted item: {}", item);
                results.add(item);
            }
        }
    }

    /**
     * 解析工具参数
     */
    private Map<String, Object> parseToolParams(String params) {
        Map<String, Object> result = new HashMap<>();
        // 简化实现，实际应该解析 JSON 或其他格式
        result.put("query", params);
        return result;
    }

    /**
     * 提取关键洞察
     */
    private List<String> extractKeyInsights(String response) {
        List<String> insights = new ArrayList<>();

        // 查找标记的关键点
        Pattern pattern = Pattern.compile("(?:关键观点|Key point|Insight)[:：](.*?)(?:\\n|$)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(response);
        while (matcher.find()) {
            insights.add(matcher.group(1).trim());
        }

        return insights;
    }

    /**
     * 获取聊天模型
     */
    private ChatLanguageModel getChatModel(String modelName) {
        return modelCache.computeIfAbsent(modelName, this::createChatModel);
    }

    /**
     * 创建聊天模型
     */
    private ChatLanguageModel createChatModel(String modelName) {
        // 根据模型名称判断提供商类型
        LlmProperties.ProviderType providerType = llmProperties.getProviderType(modelName);

        log.debug("Creating model: {}, provider: {}", modelName, providerType);

        switch (providerType) {
            case ANTHROPIC:
                return createAnthropicModel(modelName);
            case OPENAI:
            default:
                return createOpenAIModel(modelName);
        }
    }

    /**
     * 创建 OpenAI 兼容模型（包括 DeepSeek、通义千问等）
     */
    private ChatLanguageModel createOpenAIModel(String modelName) {
        String apiKey = llmProperties.getApiKey(modelName);
        if (apiKey == null || apiKey.trim().isEmpty() || apiKey.equals("your-api-key-here")) {
            throw new IllegalStateException("OpenAI API key is not configured in application.yml");
        }

        String baseUrl = llmProperties.getBaseUrl(modelName);
        Double temperature = llmProperties.getTemperature(modelName);
        Integer maxTokens = llmProperties.getMaxTokens(modelName);
        Duration timeout = llmProperties.getOpenai().getTimeoutAsDuration();

        log.debug("OpenAI Model - baseUrl: {}, model: {}, temperature: {}, maxTokens: {}",
                baseUrl, modelName, temperature, maxTokens);

        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .timeout(timeout)
                .build();
    }

    /**
     * 创建 Anthropic 模型
     */
    private ChatLanguageModel createAnthropicModel(String modelName) {
        String apiKey = llmProperties.getApiKey(modelName);
        if (apiKey == null || apiKey.trim().isEmpty() || apiKey.equals("your-api-key-here")) {
            throw new IllegalStateException("Anthropic API key is not configured in application.yml");
        }

        String baseUrl = llmProperties.getBaseUrl(modelName);
        Double temperature = llmProperties.getTemperature(modelName);
        Integer maxTokens = llmProperties.getMaxTokens(modelName);
        Duration timeout = llmProperties.getAnthropic().getTimeoutAsDuration();

        log.debug("Anthropic Model - baseUrl: {}, model: {}, temperature: {}, maxTokens: {}",
                baseUrl, modelName, temperature, maxTokens);

        return AnthropicChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .timeout(timeout)
                .build();
    }

    /**
     * 获取流式聊天模型
     */
    private StreamingChatLanguageModel getStreamingChatModel(String modelName) {
        return streamingModelCache.computeIfAbsent(modelName, this::createStreamingChatModel);
    }

    /**
     * 创建流式聊天模型
     */
    private StreamingChatLanguageModel createStreamingChatModel(String modelName) {
        // 根据模型名称判断提供商类型
        LlmProperties.ProviderType providerType = llmProperties.getProviderType(modelName);

        log.debug("Creating streaming model: {}, provider: {}", modelName, providerType);

        switch (providerType) {
            case ANTHROPIC:
                return createAnthropicStreamingModel(modelName);
            case OPENAI:
            default:
                return createOpenAIStreamingModel(modelName);
        }
    }

    /**
     * 创建 OpenAI 兼容的流式模型
     */
    private StreamingChatLanguageModel createOpenAIStreamingModel(String modelName) {
        String apiKey = llmProperties.getApiKey(modelName);
        if (apiKey == null || apiKey.trim().isEmpty() || apiKey.equals("your-api-key-here")) {
            throw new IllegalStateException("OpenAI API key is not configured in application.yml");
        }

        String baseUrl = llmProperties.getBaseUrl(modelName);
        Double temperature = llmProperties.getTemperature(modelName);
        Integer maxTokens = llmProperties.getMaxTokens(modelName);
        Duration timeout = llmProperties.getOpenai().getTimeoutAsDuration();

        log.debug("OpenAI Streaming Model - baseUrl: {}, model: {}, temperature: {}, maxTokens: {}",
                baseUrl, modelName, temperature, maxTokens);

        return OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .timeout(timeout)
                .build();
    }

    /**
     * 创建 Anthropic 流式模型
     */
    private StreamingChatLanguageModel createAnthropicStreamingModel(String modelName) {
        String apiKey = llmProperties.getApiKey(modelName);
        if (apiKey == null || apiKey.trim().isEmpty() || apiKey.equals("your-api-key-here")) {
            throw new IllegalStateException("Anthropic API key is not configured in application.yml");
        }

        String baseUrl = llmProperties.getBaseUrl(modelName);
        Double temperature = llmProperties.getTemperature(modelName);
        Integer maxTokens = llmProperties.getMaxTokens(modelName);
        Duration timeout = llmProperties.getAnthropic().getTimeoutAsDuration();

        log.debug("Anthropic Streaming Model - baseUrl: {}, model: {}, temperature: {}, maxTokens: {}",
                baseUrl, modelName, temperature, maxTokens);

        return AnthropicStreamingChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .timeout(timeout)
                .build();
    }

    /**
     * 流式执行 Agent
     *
     * @param agent Agent 实例
     * @param context 会话上下文
     * @param previousSpeaker 前一位发言者
     * @return 流式 token 输出
     */
    public Flux<String> streamExecute(AgentInstance agent, SessionContext context, String previousSpeaker) {
        return streamExecute(agent, context, previousSpeaker, null);
    }

    /**
     * 流式执行 Agent（带响应回调）
     *
     * @param agent Agent 实例
     * @param context 会话上下文
     * @param previousSpeaker 前一位发言者
     * @param responseCallback 响应完成后的回调
     * @return 流式 token 输出
     */
    public Flux<String> streamExecute(AgentInstance agent, SessionContext context, String previousSpeaker,
                                     java.util.function.Consumer<AgentResponse> responseCallback) {
        return Flux.create(sink -> {
            try {
                // 1. 构建系统提示
                String systemPrompt = buildSystemPrompt(agent, context, previousSpeaker);

                // 2. 构建用户消息
                String userMessage = buildUserMessage(agent, context, previousSpeaker);

                // 3. 获取流式聊天模型
                StreamingChatLanguageModel model = getStreamingChatModel(agent.getModel());

                // 4. 构建消息列表
                List<ChatMessage> messages = Arrays.asList(
                        SystemMessage.from(systemPrompt),
                        UserMessage.from(userMessage)
                );

                // 5. 创建 ObjectMapper 用于 JSON 序列化
                ObjectMapper objectMapper = new ObjectMapper();

                // 6. 发送开始标记
                Map<String, Object> startData = new HashMap<>();
                startData.put("type", "start");
                startData.put("agentId", agent.getInstanceId());
                startData.put("roleName", agent.getRoleDefinition().getRoleId());
                startData.put("roleDisplayName", agent.getRoleDefinition().getDisplayName());
                startData.put("round", context.getCurrentRound());
                sink.next(objectMapper.writeValueAsString(startData));

                // 7. 使用真正的流式 API
                log.info("Starting real streaming for agent {}", agent.getInstanceId());

                model.generate(messages, new StreamingResponseHandler<>() {
                    private final StringBuilder fullResponse = new StringBuilder();
                    private final long startTime = System.currentTimeMillis();

                    @Override
                    public void onNext(String token) {
                        try {
                            // 实时发送每个 token
                            Map<String, Object> tokenData = new HashMap<>();
                            tokenData.put("type", "token");
                            tokenData.put("content", token);
                            sink.next(objectMapper.writeValueAsString(tokenData));

                            // 累积完整响应
                            fullResponse.append(token);

                        } catch (JsonProcessingException e) {
                            log.error("Error serializing token", e);
                        }
                    }

                    @Override
                    public void onComplete(Response<AiMessage> response) {
                        try {
                            log.info("Streaming completed for agent {}, total length: {}",
                                    agent.getInstanceId(), fullResponse.length());

                            // 解析完整响应
                            String responseText = fullResponse.toString();
                            AgentResponse agentResponse = parseResponse(
                                    agent, responseText, startTime, context.getCurrentRound());

                            // 调用回调（如果提供）
                            if (responseCallback != null) {
                                responseCallback.accept(agentResponse);
                            }

                            // 发送完成标记
                            Map<String, Object> completeData = new HashMap<>();
                            completeData.put("type", "complete");
                            completeData.put("response", agentResponse);
                            sink.next(objectMapper.writeValueAsString(completeData));

                            sink.complete();

                        } catch (Exception e) {
                            log.error("Error completing stream for agent {}",
                                    agent.getInstanceId(), e);
                            sink.error(e);
                        }
                    }

                    @Override
                    public void onError(Throwable error) {
                        log.error("Streaming error for agent {}",
                                agent.getInstanceId(), error);
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
