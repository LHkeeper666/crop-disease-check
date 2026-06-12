package com.agriculture.modules.agriBrain.service.impl;

import com.agriculture.common.config.LlmProperties;
import com.agriculture.modules.agriBrain.entity.AiConversation;
import com.agriculture.modules.agriBrain.entity.AiMessage;
import com.agriculture.common.exception.BusinessException;
import com.agriculture.modules.agriBrain.service.AgriBrainService;
import com.agriculture.modules.agriBrain.service.AiConversationService;
import com.agriculture.modules.agriBrain.service.AiMessageService;
import com.agriculture.modules.agriBrain.vo.ChatEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
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

    private static final String SYSTEM_PROMPT = "你是一位资深农业遥测专家 AI 助手，隶属于 TreeForge 智慧农业遥测平台。你的知识涵盖以下领域：\n"
            + "\n"
            + "【核心专长】\n"
            + "- 作物病虫害识别与防治（番茄晚疫病、白粉病、灰霉病、霜霉病、红蜘蛛、蚜虫、螟虫、白粉虱等）\n"
            + "- 农业环境监测与调控（温度、湿度、土壤水分、光照、CO₂浓度）\n"
            + "- 土壤养分分析（N/P/K、pH值、EC电导率）\n"
            + "- 温室智能化管理（通风、灌溉、施肥策略）\n"
            + "- 农药与肥料使用规范（安全间隔期、配比建议）\n"
            + "\n"
            + "【数据感知能力】\n"
            + "你可以访问以下遥测数据进行分析：\n"
            + "- 环境参数：空气温度、土壤湿度、空气湿度、光照强度\n"
            + "- 能耗数据：当前功耗与最大负载\n"
            + "- 生长指标：CO₂、土壤pH、EC、温度、N/P/K含量\n"
            + "- 温室元数据：区域编号、作物种类、定植日期、地理位置、面积\n"
            + "- 网格热力图：各区域风险评分与病虫害类型\n"
            + "- 工单系统：告警级别(PENDING/PROCESSING/DONE/IGNORED)、置信度、处理状态\n"
            + "- 历史统计：总上报数、日趋势、病害/虫害分布\n"
            + "\n"
            + "【回答规范】\n"
            + "1. 使用中文回答，语气专业但易懂\n"
            + "2. 涉及数值时必须使用精确数据，格式如 \"23.6°C\"、\"65.2%\"\n"
            + "3. 对于病虫害问题，给出：风险等级、传播概率、推荐防治措施、安全用药建议\n"
            + "4. 对于环境异常，给出：原因分析、调控建议、预期改善时间\n"
            + "5. 必要时提供分级建议（紧急/重要/常规）\n"
            + "6. 回答末尾给出可执行的行动建议";

    private static final String QUICK_ADVICE_PROMPT = "基于当前农业监测系统的数据，请生成一份全面的农业管理建议报告。"
            + "请从以下维度给出建议：\n"
            + "1. 当前环境参数分析与调控建议\n"
            + "2. 病虫害风险评估与预防措施\n"
            + "3. 灌溉与施肥建议\n"
            + "4. 温室管理优化建议\n"
            + "5. 近期重点工作安排";

    private static final ExecutorService executor = Executors.newCachedThreadPool();

    @Resource
    private LlmProperties llmProperties;

    @Resource
    private RestClient llmRestClient;

    @Resource
    private AiConversationService conversationService;

    @Resource
    private AiMessageService messageService;

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
                List<Map<String, String>> messages = buildMessages(history, message, SYSTEM_PROMPT);

                // 调用 LLM 流式 API
                StringBuilder fullResponse = new StringBuilder();
                streamLlmResponse(messages, emitter, fullResponse);

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

                List<Map<String, String>> messages = new ArrayList<>();
                messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
                messages.add(Map.of("role", "user", "content", QUICK_ADVICE_PROMPT));

                StringBuilder fullResponse = new StringBuilder();
                streamLlmResponse(messages, emitter, fullResponse);

                messageService.saveMessage(conversation.getId(), userId, "USER", QUICK_ADVICE_PROMPT);
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
            // 返回指定对话的消息列表
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
            // 返回用户最近的对话列表
            return null; // 由 Controller 层处理分页查询
        }
    }

    private List<Map<String, String>> buildMessages(List<AiMessage> history, String userMessage, String systemPrompt) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));

        for (AiMessage msg : history) {
            messages.add(Map.of("role", msg.getRole().toLowerCase(), "content", msg.getContent()));
        }

        messages.add(Map.of("role", "user", "content", userMessage));
        return messages;
    }

    private void streamLlmResponse(List<Map<String, String>> messages, SseEmitter emitter, StringBuilder fullResponse) throws Exception {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", llmProperties.getModel());
        requestBody.put("messages", messages);
        requestBody.put("stream", true);
        requestBody.put("max_tokens", 2048);

        String bodyJson = objectMapper.writeValueAsString(requestBody);

        llmRestClient.post()
                .uri("/v1/chat/completions")
                .header("Accept", "text/event-stream")
                .body(bodyJson)
                .exchange((request, response) -> {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.isBlank()) continue;
                            if (!line.startsWith("data: ")) continue;

                            String data = line.substring(6).trim();
                            if ("[DONE]".equals(data)) break;

                            try {
                                JsonNode json = objectMapper.readTree(data);
                                JsonNode choices = json.get("choices");
                                if (choices != null && choices.isArray() && !choices.isEmpty()) {
                                    JsonNode delta = choices.get(0).get("delta");
                                    if (delta != null && delta.has("content")) {
                                        String content = delta.get("content").asText();
                                        fullResponse.append(content);
                                        emitter.send(ChatEvent.token(content));
                                    }
                                }
                            } catch (Exception e) {
                                log.debug("SSE 行解析跳过: {}", line);
                            }
                        }
                    }
                    return null;
                });
    }
}
