package com.agriculture.modules.agriBrain.service.impl;

import com.agriculture.modules.agriBrain.entity.AiConversation;
import com.agriculture.modules.agriBrain.mapper.AiConversationMapper;
import com.agriculture.modules.agriBrain.service.AiConversationService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AiConversationServiceImpl extends ServiceImpl<AiConversationMapper, AiConversation> implements AiConversationService {

    @Override
    public AiConversation createConversation(String userId, String title) {
        AiConversation conversation = new AiConversation();
        conversation.setId(UUID.randomUUID().toString().replace("-", ""));
        conversation.setUserId(userId);
        conversation.setTitle(title);
        conversation.setCreatedAt(LocalDateTime.now());
        conversation.setUpdatedAt(LocalDateTime.now());
        baseMapper.insert(conversation);
        return conversation;
    }

    @Override
    public void updateUpdatedAt(String id) {
        AiConversation conversation = new AiConversation();
        conversation.setId(id);
        conversation.setUpdatedAt(LocalDateTime.now());
        baseMapper.updateById(conversation);
    }
}
