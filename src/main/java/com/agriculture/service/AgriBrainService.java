package com.agriculture.service;

import com.agriculture.vo.ChatEvent;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

public interface AgriBrainService {

    SseEmitter chat(String message, String conversationId, String userId);

    SseEmitter quickAdvice(String userId);

    List<Map<String, Object>> getHistory(String conversationId, String userId, int page, int size);
}
