package com.agriculture.controller;

import com.agriculture.dto.ChatRequest;
import com.agriculture.entity.AiConversation;
import com.agriculture.service.AgriBrainService;
import com.agriculture.service.AiConversationService;
import com.agriculture.vo.ConversationVO;
import com.agriculture.vo.Result;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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

    @PostMapping("/chat")
    public SseEmitter chat(@RequestBody ChatRequest request) {
        // TODO: 从 SecurityContext 获取当前用户ID，暂时使用默认值
        String userId = "system";
        return agriBrainService.chat(request.getMessage(), request.getConversationId(), userId);
    }

    @PostMapping("/quick-advice")
    public SseEmitter quickAdvice() {
        String userId = "system";
        return agriBrainService.quickAdvice(userId);
    }

    @GetMapping("/history")
    public Result<?> getHistory(
            @RequestParam(required = false) String conversationId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        String userId = "system";

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
}
