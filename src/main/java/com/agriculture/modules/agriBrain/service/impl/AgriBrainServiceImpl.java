package com.agriculture.modules.agriBrain.service.impl;

import com.agriculture.common.config.LlmProperties;
import com.agriculture.common.service.TemplateService;
import com.agriculture.modules.agriBrain.dto.PageContext;
import com.agriculture.modules.agriBrain.entity.AiConversation;
import com.agriculture.modules.agriBrain.entity.AiMessage;
import com.agriculture.common.exception.BusinessException;
import com.agriculture.modules.agriBrain.service.AgriBrainService;
import com.agriculture.modules.agriBrain.service.AiConfigService;
import com.agriculture.modules.agriBrain.service.AiConversationService;
import com.agriculture.modules.agriBrain.service.AiMessageService;
import com.agriculture.modules.agriBrain.service.ContextBuilder;
import com.agriculture.modules.agriBrain.tool.AiTool;
import com.agriculture.modules.agriBrain.tool.AiToolRegistry;
import com.agriculture.modules.agriBrain.vo.ChatEvent;
import com.agriculture.modules.user.entity.SysUser;
import com.agriculture.modules.user.mapper.SysUserMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class AgriBrainServiceImpl implements AgriBrainService {

    private static final Logger log = LoggerFactory.getLogger(AgriBrainServiceImpl.class);

    private String buildSystemPrompt(String context) {
        java.time.LocalDate today = java.time.LocalDate.now();
        String dateStr = today.toString(); // 2026-06-13
        String dayOfWeek = today.getDayOfWeek().getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.CHINA); // 周五

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("date", dateStr);
        attributes.put("dayOfWeek", dayOfWeek);
        attributes.put("context", context != null ? context : "");

        return templateService.render("system_prompt", attributes);
    }

    private static final int MAX_TOOL_ROUNDS = 5;

    private static final ExecutorService executor = Executors.newCachedThreadPool();

    @Resource
    private LlmProperties llmProperties;

    @Resource
    private RestClient llmRestClient;

    @Resource
    private AiConversationService conversationService;

    @Resource
    private AiMessageService messageService;

    @Resource
    private AiConfigService configService;

    @Resource
    private AiToolRegistry toolRegistry;

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private TemplateService templateService;

    @Resource
    private ContextBuilder contextBuilder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public SseEmitter chat(String message, String conversationId, String userId) {
        return chat(message, conversationId, userId, null);
    }

    @Override
    public SseEmitter chat(String message, String conversationId, String userId, PageContext context) {
        SseEmitter emitter = new SseEmitter(120_000L);

        executor.submit(() -> {
            try {
                // 获取用户企业ID
                String companyId = resolveCompanyId(userId);

                // 构建页面上下文
                String contextText = contextBuilder.buildContext(context, userId, companyId);

                // 获取或创建对话
                AiConversation conversation;
                if (conversationId == null || conversationId.isBlank()) {
                    String title = message.length() > 50 ? message.substring(0, 50) + "..." : message;
                    conversation = conversationService.createConversation(userId, title, companyId);
                } else {
                    conversation = conversationService.getById(conversationId);
                    if (conversation == null) {
                        emitter.send(ChatEvent.error("对话不存在"));
                        emitter.complete();
                        return;
                    }
                }

                // 加载历史消息
                List<AiMessage> history = messageService.listByConversationId(
                        conversation.getId(), llmProperties.getMaxHistoryMessages());

                // 构造 LLM 请求
                List<Map<String, Object>> messages = buildMessages(history, message, buildSystemPrompt(contextText));
                List<Map<String, Object>> tools = toolRegistry.buildToolDefinitions();

                // 获取动态配置
                String apiKey = getApiKey();
                String model = getModel();

                // 执行 tool calling 循环
                StringBuilder fullResponse = new StringBuilder();
                StringBuilder toolContext = new StringBuilder();
                executeToolCallingLoop(messages, tools, emitter, fullResponse, toolContext, apiKey, model, userId, companyId);

                // 持久化消息（tool 结果拼接到回答前面，供下轮对话参考）
                messageService.saveMessage(conversation.getId(), userId, "USER", message, companyId);
                String assistantContent = toolContext.length() > 0
                        ? toolContext + "\n---\n" + fullResponse
                        : fullResponse.toString();
                messageService.saveMessage(conversation.getId(), userId, "ASSISTANT", assistantContent, companyId);
                conversationService.updateUpdatedAt(conversation.getId());

                // 发送完成事件
                emitter.send(ChatEvent.done(conversation.getId()));
                emitter.complete();

            } catch (Exception e) {
                log.error("农业大脑对话异常", e);
                try {
                    emitter.send(ChatEvent.error("对话处理异常: " + e.getMessage()));
                } catch (Exception ignored) {
                }
                emitter.completeWithError(e);
            }
        });

        emitter.onTimeout(emitter::complete);
        emitter.onError(t -> log.warn("SSE 连接异常", t));

        return emitter;
    }

    @Override
    public SseEmitter quickAdvice(String userId) {
        SseEmitter emitter = new SseEmitter(120_000L);

        executor.submit(() -> {
            try {
                String title = "一键建议 - " + java.time.LocalDate.now();
                String companyId = resolveCompanyId(userId);
                AiConversation conversation = conversationService.createConversation(userId, title, companyId);

                String quickAdvicePrompt = templateService.render("quick_advice", null);

                List<Map<String, Object>> messages = new ArrayList<>();
                messages.add(Map.of("role", "system", "content", buildSystemPrompt(null)));
                messages.add(Map.of("role", "user", "content", quickAdvicePrompt));

                List<Map<String, Object>> tools = toolRegistry.buildToolDefinitions();

                // 获取动态配置
                String apiKey = getApiKey();
                String model = getModel();

                StringBuilder fullResponse = new StringBuilder();
                StringBuilder toolContext = new StringBuilder();
                executeToolCallingLoop(messages, tools, emitter, fullResponse, toolContext, apiKey, model, userId, companyId);

                messageService.saveMessage(conversation.getId(), userId, "USER", quickAdvicePrompt, companyId);
                String assistantContent = toolContext.length() > 0
                        ? toolContext + "\n---\n" + fullResponse
                        : fullResponse.toString();
                messageService.saveMessage(conversation.getId(), userId, "ASSISTANT", assistantContent, companyId);

                emitter.send(ChatEvent.done(conversation.getId()));
                emitter.complete();

            } catch (Exception e) {
                log.error("一键建议生成异常", e);
                try {
                    emitter.send(ChatEvent.error("建议生成异常: " + e.getMessage()));
                } catch (Exception ignored) {
                }
                emitter.completeWithError(e);
            }
        });

        emitter.onTimeout(emitter::complete);
        emitter.onError(t -> log.warn("SSE 连接异常", t));

        return emitter;
    }

    @Override
    public List<Map<String, Object>> getHistory(String conversationId, String userId, int page, int size) {
        if (conversationId != null && !conversationId.isBlank()) {
            List<AiMessage> messages = messageService.listByConversationId(conversationId, 1000);
            List<Map<String, Object>> result = new ArrayList<>();
            for (AiMessage msg : messages) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", msg.getId());
                item.put("conversationId", msg.getConversationId());
                item.put("role", msg.getRole());
                item.put("content", msg.getContent());
                item.put("createdAt", msg.getCreatedAt());
                result.add(item);
            }
            return result;
        } else {
            return null;
        }
    }

    private List<Map<String, Object>> buildMessages(List<AiMessage> history, String userMessage, String systemPrompt) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));

        // 发送完整历史，剥离 tool context，并清理消息顺序确保 user/assistant 严格交替
        String lastRole = "system";
        for (AiMessage msg : history) {
            String role = msg.getRole().toLowerCase();
            String content = msg.getContent();

            // 跳过 tool 消息（tool_calls 和 tool_result 在持久化时不应出现，但以防万一）
            if ("tool".equals(role)) continue;

            // 剥离 tool context
            if ("assistant".equals(role) && content.contains("\n---\n")) {
                content = content.substring(content.indexOf("\n---\n") + 5);
            }

            // 跳过空内容
            if (content == null || content.isBlank()) continue;

            // 确保严格交替：连续相同 role 时合并或跳过
            if (role.equals(lastRole)) {
                if ("assistant".equals(role)) {
                    // 连续 assistant：跳过前一个（保留最新的）
                    messages.remove(messages.size() - 1);
                } else {
                    // 连续 user：跳过当前（保留前一个）
                    continue;
                }
            }

            messages.add(Map.of("role", role, "content", content));
            lastRole = role;
        }

        // 确保最后一条不是 user（避免连续 user）
        if (!messages.isEmpty() && "user".equals(messages.get(messages.size() - 1).get("role"))) {
            messages.remove(messages.size() - 1);
        }

        messages.add(Map.of("role", "user", "content", userMessage));
        return messages;
    }

    private String resolveCompanyId(String userId) {
        if (userId == null) return "";
        SysUser user = sysUserMapper.selectById(userId);
        if (user != null && StringUtils.hasText(user.getCompanyId())) {
            return user.getCompanyId();
        }
        return "";
    }

    private String getApiKey() {
        String apiKey = configService.getConfigValue("apiKey");
        return (apiKey != null && !apiKey.isEmpty()) ? apiKey : llmProperties.getApiKey();
    }

    private String getModel() {
        String model = configService.getConfigValue("model");
        return (model != null && !model.isEmpty()) ? model : llmProperties.getModel();
    }

    /**
     * Tool calling 循环：调用 LLM → 检测 tool_calls → 执行工具 → 再次调用 LLM
     */
    private void executeToolCallingLoop(List<Map<String, Object>> messages, List<Map<String, Object>> tools,
                                         SseEmitter emitter, StringBuilder fullResponse, StringBuilder toolContext,
                                         String apiKey, String model, String userId, String companyId) throws Exception {
        int round = 0;

        while (round < MAX_TOOL_ROUNDS) {
            round++;
            log.info("Tool calling 第 {} 轮", round);

            // 调用 LLM
            ToolCallResult result = streamLlmResponseWithToolCalls(messages, tools, emitter, fullResponse, apiKey, model);

            // 如果没有 tool_calls，流程结束
            if (result.toolCalls == null || result.toolCalls.isEmpty()) {
                log.info("第 {} 轮无 tool_calls，流程结束", round);
                break;
            }

            // 有 tool_calls，执行工具
            log.info("第 {} 轮收到 {} 个 tool_calls", round, result.toolCalls.size());

            // 将 assistant 的 tool_calls 消息加入 messages
            Map<String, Object> assistantMsg = new LinkedHashMap<>();
            assistantMsg.put("role", "assistant");
            assistantMsg.put("tool_calls", result.toolCalls);
            messages.add(assistantMsg);

            // 本轮已通知的 tool name（去重，同一 tool 只通知一次）
            Set<String> notifiedTools = new HashSet<>();

            // 执行每个 tool call
            for (Map<String, Object> toolCall : result.toolCalls) {
                log.info("toolCall 原始数据: {}", toolCall);
                String toolCallId = (String) toolCall.get("id");
                Map<String, Object> function = (Map<String, Object>) toolCall.get("function");
                String toolName = function != null ? (String) function.get("name") : null;
                String argsJson = function != null ? (String) function.get("arguments") : null;

                // 防止 null 值
                if (toolCallId == null) toolCallId = "call_" + System.currentTimeMillis();
                if (toolName == null) toolName = "unknown";

                log.info("执行工具: id={}, name={}, args={}", toolCallId, toolName, argsJson);

                // 发送 tool_call 事件（同一轮同一 tool 只通知一次）
                if (notifiedTools.add(toolName)) {
                    emitter.send(ChatEvent.toolCall(toolCallId, toolName));
                }

                // 解析参数
                Map<String, Object> arguments = new LinkedHashMap<>();
                if (StringUtils.hasText(argsJson)) {
                    try {
                        arguments = objectMapper.readValue(argsJson, Map.class);
                    } catch (Exception e) {
                        log.warn("工具参数解析失败: {}", argsJson, e);
                    }
                }

                // 执行工具
                String toolResult = executeTool(toolName, arguments, userId, companyId);
                log.info("工具执行结果: {}", toolResult);

                // 收集 tool 结果用于持久化（截断过长结果避免干扰后续对话）
                if (toolContext.length() > 0) toolContext.append("\n");
                String truncatedResult = toolResult.length() > 500
                        ? toolResult.substring(0, 500) + "...(truncated)"
                        : toolResult;
                toolContext.append("[").append(toolName).append("] ").append(truncatedResult);

                // 发送 tool_result 事件
                emitter.send(ChatEvent.toolResult(toolCallId, toolName, toolResult));

                // 将 tool result 加入 messages
                Map<String, Object> toolMsg = new LinkedHashMap<>();
                toolMsg.put("role", "tool");
                toolMsg.put("tool_call_id", toolCallId);
                toolMsg.put("content", toolResult);
                messages.add(toolMsg);
            }
        }

        // 如果循环用完所有轮次（LLM 一直在调 tool），强制最终一次不带 tools 的调用让 LLM 生成回答
        if (round >= MAX_TOOL_ROUNDS) {
            log.warn("Tool calling 达到最大轮次 {}，强制最终回答", MAX_TOOL_ROUNDS);
            streamLlmResponseWithToolCalls(messages, null, emitter, fullResponse, apiKey, model);
        }
    }

    private String executeTool(String toolName, Map<String, Object> arguments, String userId, String companyId) {
        AiTool tool = toolRegistry.getTool(toolName);
        if (tool == null) {
            return "{\"error\": \"未知工具: " + toolName + "\"}";
        }
        try {
            return tool.execute(arguments, userId, companyId);
        } catch (Exception e) {
            log.error("工具执行异常: {}", toolName, e);
            return "{\"error\": \"工具执行异常: " + e.getMessage() + "\"}";
        }
    }

    /**
     * 流式调用 LLM，支持 tool_calls 解析
     */
    private ToolCallResult streamLlmResponseWithToolCalls(List<Map<String, Object>> messages,
                                                           List<Map<String, Object>> tools,
                                                           SseEmitter emitter,
                                                           StringBuilder fullResponse,
                                                           String apiKey, String model) throws Exception {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", messages);
        if (tools != null && !tools.isEmpty()) {
            requestBody.put("tools", tools);
        }
        requestBody.put("stream", true);
        requestBody.put("max_tokens", 2048);

        String bodyJson = objectMapper.writeValueAsString(requestBody);

        // 打印发送给 LLM 的消息结构（不含完整内容，只打印 role 和 content 长度）
        for (int i = 0; i < messages.size(); i++) {
            Map<String, Object> m = messages.get(i);
            String role = (String) m.get("role");
            Object content = m.get("content");
            int len = content instanceof String ? ((String) content).length() : 0;
            log.info("LLM messages[{}]: role={}, contentLen={}", i, role, len);
        }

        log.info("调用 LLM API: baseUrl={}, model={}, apiKey={}***", llmProperties.getBaseUrl(), model,
                apiKey != null && apiKey.length() > 6 ? apiKey.substring(0, 6) : "null");

        RestClient client = RestClient.builder()
                .baseUrl(llmProperties.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();

        ToolCallResult toolCallResult = new ToolCallResult();

        client.post()
                .uri("/v1/chat/completions")
                .header("Accept", "text/event-stream")
                .body(bodyJson)
                .exchange((request, response) -> {
                    log.info("LLM API 响应状态: {}", response.getStatusCode());

                    if (!response.getStatusCode().is2xxSuccessful()) {
                        String errorBody = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        log.error("LLM API 调用失败: {}", errorBody);
                        try {
                            emitter.send(ChatEvent.error("LLM API 调用失败: " + response.getStatusCode()));
                        } catch (Exception ignored) {
                        }
                        return null;
                    }

                    // 用于累积 tool_calls 的 map: index -> {id, name, arguments}
                    Map<Integer, Map<String, Object>> toolCallsMap = new LinkedHashMap<>();
                    boolean hasToolCalls = false;

                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.isBlank()) continue;

                            log.debug("收到 SSE 行: {}", line);

                            String data = null;
                            if (line.startsWith("data: ")) {
                                data = line.substring(6).trim();
                            } else if (line.startsWith("data:")) {
                                data = line.substring(5).trim();
                            } else {
                                continue;
                            }

                            if ("[DONE]".equals(data)) break;

                            try {
                                JsonNode json = objectMapper.readTree(data);
                                JsonNode choices = json.get("choices");
                                if (choices != null && choices.isArray() && !choices.isEmpty()) {
                                    JsonNode choice = choices.get(0);
                                    JsonNode delta = choice.get("delta");
                                    JsonNode finishReason = choice.get("finish_reason");

                                    // 处理 content
                                    if (delta != null && delta.has("content") && !delta.get("content").isNull()) {
                                        String content = delta.get("content").asText();
                                        if (content != null && !content.isEmpty()) {
                                            fullResponse.append(content);
                                            emitter.send(ChatEvent.token(content));
                                        }
                                    }

                                    // 处理 tool_calls
                                    if (delta != null && delta.has("tool_calls")) {
                                        hasToolCalls = true;
                                        JsonNode toolCalls = delta.get("tool_calls");
                                        log.info("收到 tool_calls delta: {}", toolCalls);
                                        if (toolCalls.isArray()) {
                                            for (JsonNode toolCallNode : toolCalls) {
                                                int index = toolCallNode.has("index") ? toolCallNode.get("index").asInt() : 0;

                                                Map<String, Object> tc = toolCallsMap.computeIfAbsent(index, k -> {
                                                    Map<String, Object> m = new LinkedHashMap<>();
                                                    m.put("id", "");
                                                    m.put("type", "function");
                                                    Map<String, Object> func = new LinkedHashMap<>();
                                                    func.put("name", "");
                                                    func.put("arguments", "");
                                                    m.put("function", func);
                                                    return m;
                                                });

                                                if (toolCallNode.has("id") && !toolCallNode.get("id").isNull()) {
                                                    String id = toolCallNode.get("id").asText();
                                                    tc.put("id", id);
                                                    log.info("设置 tool_call id: {}", id);
                                                }
                                                if (toolCallNode.has("type")) {
                                                    tc.put("type", toolCallNode.get("type").asText());
                                                }
                                                if (toolCallNode.has("function")) {
                                                    JsonNode funcNode = toolCallNode.get("function");
                                                    Map<String, Object> func = (Map<String, Object>) tc.get("function");
                                                    if (funcNode.has("name") && !funcNode.get("name").isNull() && !funcNode.get("name").asText().isEmpty()) {
                                                        String name = funcNode.get("name").asText();
                                                        func.put("name", name);
                                                        log.info("设置 tool_call name: {}", name);
                                                    }
                                                    if (funcNode.has("arguments") && !funcNode.get("arguments").isNull()) {
                                                        String args = funcNode.get("arguments").asText();
                                                        func.put("arguments", func.get("arguments") + args);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                log.debug("SSE 行解析跳过: {}", line);
                            }
                        }
                    }

                    // 如果有 tool_calls，设置结果
                    if (hasToolCalls && !toolCallsMap.isEmpty()) {
                        toolCallResult.toolCalls = new ArrayList<>(toolCallsMap.values());
                        log.info("解析到 {} 个 tool_calls", toolCallResult.toolCalls.size());
                        for (Map<String, Object> tc : toolCallResult.toolCalls) {
                            log.info("tool_call 详情: {}", tc);
                        }
                    }

                    return null;
                });

        return toolCallResult;
    }

    private static class ToolCallResult {
        List<Map<String, Object>> toolCalls;
    }

    @Override
    public String chatSync(String message, String userId) {
        String apiKey = getApiKey();
        String model = getModel();

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", buildSystemPrompt(null)));
        messages.add(Map.of("role", "user", "content", message));

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", messages);
        requestBody.put("stream", false);
        requestBody.put("max_tokens", 2048);

        try {
            String bodyJson = objectMapper.writeValueAsString(requestBody);

            RestClient client = RestClient.builder()
                    .baseUrl(llmProperties.getBaseUrl())
                    .defaultHeader("Authorization", "Bearer " + apiKey)
                    .defaultHeader("Content-Type", "application/json")
                    .build();

            String responseBody = client.post()
                    .uri("/v1/chat/completions")
                    .body(bodyJson)
                    .retrieve()
                    .body(String.class);

            JsonNode json = objectMapper.readTree(responseBody);
            JsonNode choices = json.get("choices");
            if (choices != null && choices.isArray() && !choices.isEmpty()) {
                JsonNode contentNode = choices.get(0).get("message").get("content");
                if (contentNode != null && !contentNode.isNull()) {
                    return contentNode.asText();
                }
            }
            return "（Agent 未能生成内容）";
        } catch (Exception e) {
            log.error("chatSync 调用失败", e);
            return "（Agent 生成失败: " + e.getMessage() + "）";
        }
    }
}
