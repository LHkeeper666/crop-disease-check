package com.agriculture.modules.agriBrain.controller;

import com.agriculture.modules.agriBrain.dto.ChatRequest;
import com.agriculture.modules.agriBrain.dto.ConfigRequest;
import com.agriculture.modules.agriBrain.entity.AiConversation;
import com.agriculture.modules.agriBrain.service.AgriBrainService;
import com.agriculture.modules.agriBrain.service.AiConfigService;
import com.agriculture.modules.agriBrain.service.AiConversationService;
import com.agriculture.modules.agriBrain.vo.ConversationVO;
import com.agriculture.common.config.LlmProperties;
import com.agriculture.common.vo.Result;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.BeanUtils;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/agri-brain")
public class AgriBrainController {

    @Resource
    private AgriBrainService agriBrainService;

    @Resource
    private AiConversationService conversationService;

    @Resource
    private AiConfigService configService;

    @Resource
    private LlmProperties llmProperties;

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody ChatRequest request, HttpServletRequest httpRequest) {
        String userId = (String) httpRequest.getAttribute("userId");
        return agriBrainService.chat(request.getMessage(), request.getConversationId(), userId, request.getContext());
    }

    @PostMapping(value = "/quick-advice", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter quickAdvice(HttpServletRequest httpRequest) {
        String userId = (String) httpRequest.getAttribute("userId");
        return agriBrainService.quickAdvice(userId);
    }

    @GetMapping("/history")
    public Result<?> getHistory(
            @RequestParam(required = false) String conversationId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest httpRequest) {

        String userId = (String) httpRequest.getAttribute("userId");

        if (conversationId != null && !conversationId.isBlank()) {
            // 返回指定对话的消息列表
            List<Map<String, Object>> messages = agriBrainService.getHistory(conversationId, userId, page, size);
            return Result.success(messages);
        } else {
            // 返回用户最近的对话列表
            LambdaQueryWrapper<AiConversation> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(AiConversation::getUserId, userId)
                   .orderByDesc(AiConversation::getUpdatedAt);

            Page<AiConversation> pageParam = new Page<>(page, size);
            Page<AiConversation> result = conversationService.page(pageParam, wrapper);

            Page<ConversationVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
            voPage.setRecords(result.getRecords().stream().map(c -> {
                ConversationVO vo = new ConversationVO();
                BeanUtils.copyProperties(c, vo);
                return vo;
            }).collect(Collectors.toList()));

            return Result.success(voPage);
        }
    }

    @GetMapping("/config")
    public Result<?> getConfig() {
        String provider = configService.getConfigValue("provider");
        String model = configService.getConfigValue("model");
        String apiKey = configService.getConfigValue("apiKey");

        Map<String, Object> config = new HashMap<>();
        config.put("provider", provider != null ? provider : "");
        config.put("model", model != null ? model : llmProperties.getModel());
        // apiKey 脱敏显示
        if (apiKey != null && !apiKey.isEmpty()) {
            config.put("apiKey", apiKey.substring(0, Math.min(6, apiKey.length())) + "***");
            config.put("hasApiKey", true);
        } else {
            config.put("apiKey", "");
            config.put("hasApiKey", false);
        }

        return Result.success(config);
    }

    @PutMapping("/config")
    public Result<?> updateConfig(@RequestBody ConfigRequest request) {
        if (request.getProvider() != null) {
            configService.setConfigValue("provider", request.getProvider());
        }
        if (request.getModel() != null) {
            configService.setConfigValue("model", request.getModel());
        }
        if (request.getApiKey() != null) {
            configService.setConfigValue("apiKey", request.getApiKey());
        }
        return Result.success("配置已保存");
    }

    @PostMapping("/config/validate")
    public Result<?> validateConfig(@RequestBody ConfigRequest request) {
        // 简单校验：apiKey 和 model 不为空
        if (request.getApiKey() == null || request.getApiKey().isBlank()) {
            return Result.error(400, "API Key 不能为空");
        }
        if (request.getModel() == null || request.getModel().isBlank()) {
            return Result.error(400, "模型不能为空");
        }
        // TODO: 可以尝试调用 LLM API 进行实际校验
        return Result.success("配置有效");
    }

    /**
     * 非流式聊天：用于 Agent 生成邮件内容等需要同步返回结果的场景
     */
    @PostMapping("/chat-sync")
    public Result<Map<String, String>> chatSync(@RequestBody ChatRequest request, HttpServletRequest httpRequest) {
        String userId = (String) httpRequest.getAttribute("userId");
        String content = agriBrainService.chatSync(request.getMessage(), userId);
        Map<String, String> data = new HashMap<>();
        data.put("content", content);
        return Result.success(data);
    }
}
