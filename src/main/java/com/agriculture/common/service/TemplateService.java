package com.agriculture.common.service;

import java.util.Map;

/**
 * 模板服务接口
 */
public interface TemplateService {

    /**
     * 渲染模板
     *
     * @param templateName 模板名称
     * @param attributes   模板属性
     * @return 渲染后的字符串
     */
    String render(String templateName, Map<String, Object> attributes);
}
