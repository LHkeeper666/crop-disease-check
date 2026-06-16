package com.agriculture.modules.agriBrain.service;

import com.agriculture.modules.agriBrain.entity.AiConversation;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 农业大脑对话表 服务类
 * </p>
 *
 * @author agriculture-team
 * @since 2026-06-09
 */
public interface AiConversationService extends IService<AiConversation> {

    AiConversation createConversation(String userId, String title, String companyId);

    void updateUpdatedAt(String id);
}
