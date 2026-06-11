package com.agriculture.service;

import com.agriculture.entity.AiMessage;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * <p>
 * 农业大脑消息表 服务类
 * </p>
 *
 * @author agriculture-team
 * @since 2026-06-09
 */
public interface AiMessageService extends IService<AiMessage> {

    AiMessage saveMessage(String conversationId, String userId, String role, String content);

    List<AiMessage> listByConversationId(String conversationId, int limit);
}
