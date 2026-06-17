package com.agriculture.modules.agriBrain.service;

import com.agriculture.modules.agriBrain.dto.PageContext;

public interface ContextBuilder {

    /**
     * 根据页面上下文构建注入 system prompt 的上下文文本
     *
     * @param context   页面上下文
     * @param userId    当前用户 ID
     * @param companyId 当前用户公司 ID
     * @return 格式化的上下文文本，为空时返回空字符串
     */
    String buildContext(PageContext context, String userId, String companyId);
}
