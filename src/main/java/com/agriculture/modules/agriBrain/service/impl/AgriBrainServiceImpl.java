package com.agriculture.modules.agriBrain.service.impl;

import com.agriculture.common.config.LlmProperties;
import com.agriculture.common.service.TemplateService;
import com.agriculture.modules.agriBrain.entity.AiConversation;
import com.agriculture.modules.agriBrain.entity.AiMessage;
import com.agriculture.common.exception.BusinessException;
import com.agriculture.modules.agriBrain.service.AgriBrainService;
import com.agriculture.modules.agriBrain.service.AiConfigService;
import com.agriculture.modules.agriBrain.service.AiConversationService;
import com.agriculture.modules.agriBrain.service.AiMessageService;
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

    private String buildSystemPrompt() {
        java.time.LocalDate today = java.time.LocalDate.now();
        String dateStr = today.toString(); // 2026-06-13
        String dayOfWeek = today.getDayOfWeek().getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.CHINA); // 周五

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("date", dateStr);
        attributes.put("dayOfWeek", dayOfWeek);

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

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public SseEmitter chat(String message, String conversationId, String userId) {
        SseEmitter emitter = new SseEmitter(120_000L);

        executor.submit(() -> {
            try {
                // 获取或创建对话
                AiConversation conversation;
                if (conversationId == null || conversationId.isBlank()) {
                    String title = message.length() > 50 ? message.substring(0, 50) + "..." : message;
                    conversation = conversationService.createConversation(userId, title);
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
                List<Map<String, Object>> messages = buildMessages(history, message, buildSystemPrompt());
                List<Map<String, Object>> tools = toolRegistry.buildToolDefinitions();

                // 获取动态配置
                String apiKey = getApiKey();
                String model = getModel();

                // 获取用户企业ID（用于权限隔离）
                String companyId = resolveCompanyId(userId);

                // 执行 tool calling 循环
                StringBuilder fullResponse = new StringBuilder();
                executeToolCallingLoop(messages, tools, emitter, fullResponse, apiKey, model, userId, companyId);

                // 持久化消息
                messageService.saveMessage(conversation.getId(), userId, "USER", message);
                messageService.saveMessage(conversation.getId(), userId, "ASSISTANT", fullResponse.toString());
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
                AiConversation conversation = conversationService.createConversation(userId, title);

                String quickAdvicePrompt = templateService.render("quick_advice", null);

                List<Map<String, Object>> messages = new ArrayList<>();
                messages.add(Map.of("role", "system", "content", buildSystemPrompt()));
                messages.add(Map.of("role", "user", "content", quickAdvicePrompt));

                List<Map<String, Object>> tools = toolRegistry.buildToolDefinitions();

                // 获取动态配置
                String apiKey = getApiKey();
                String model = getModel();

                String companyId = resolveCompanyId(userId);

                StringBuilder fullResponse = new StringBuilder();
                executeToolCallingLoop(messages, tools, emitter, fullResponse, apiKey, model, userId, companyId);

                messageService.saveMessage(conversation.getId(), userId, "USER", quickAdvicePrompt);
                messageService.saveMessage(conversation.getId(), userId, "ASSISTANT", fullResponse.toString());

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

        for (AiMessage msg : history) {
            messages.add(Map.of("role", msg.getRole().toLowerCase(), "content", msg.getContent()));
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
                                         SseEmitter emitter, StringBuilder fullResponse,
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

                // 发送 tool_call 事件
                emitter.send(ChatEvent.toolCall(toolCallId, toolName));

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
                                            log.debug("发送 token: {}", content);
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
}
