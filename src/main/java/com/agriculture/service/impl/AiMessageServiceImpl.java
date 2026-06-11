package com.agriculture.service.impl;

import com.agriculture.entity.AiMessage;
import com.agriculture.dao.mapper.AiMessageMapper;
import com.agriculture.service.AiMessageService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class AiMessageServiceImpl extends ServiceImpl<AiMessageMapper, AiMessage> implements AiMessageService {

    @Override
    public AiMessage saveMessage(String conversationId, String userId, String role, String content) {
        AiMessage message = new AiMessage();
        message.setId(UUID.randomUUID().toString().replace("-", ""));
        message.setConversationId(conversationId);
        message.setUserId(userId);
        message.setRole(role);
        message.setContent(content);
        message.setCreatedAt(LocalDateTime.now());
        baseMapper.insert(message);
        return message;
    }

    @Override
    public List<AiMessage> listByConversationId(String conversationId, int limit) {
        LambdaQueryWrapper<AiMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiMessage::getConversationId, conversationId)
               .orderByAsc(AiMessage::getCreatedAt)
               .last("LIMIT " + limit);
        return baseMapper.selectList(wrapper);
    }
}
