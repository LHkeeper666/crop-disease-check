package com.agriculture.modules.agriBrain.service;

import com.agriculture.modules.agriBrain.dto.PageContext;
import com.agriculture.modules.agriBrain.vo.ChatEvent;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

public interface AgriBrainService {

    SseEmitter chat(String message, String conversationId, String userId);

    SseEmitter chat(String message, String conversationId, String userId, PageContext context);

    SseEmitter quickAdvice(String userId);

    List<Map<String, Object>> getHistory(String conversationId, String userId, int page, int size);

    /**
     * 非流式聊天：同步返回完整回答（用于邮件生成等场景）
     */
    String chatSync(String message, String userId);
}
