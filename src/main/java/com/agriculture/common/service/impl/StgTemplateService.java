package com.agriculture.common.service.impl;

import com.agriculture.common.service.TemplateService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.stringtemplate.v4.ST;
import org.stringtemplate.v4.STGroup;
import org.stringtemplate.v4.STGroupFile;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * StringTemplate 模板服务实现
 */
@Service
public class StgTemplateService implements TemplateService {

    private static final Logger log = LoggerFactory.getLogger(StgTemplateService.class);

    private static final String TEMPLATE_BASE_PATH = "templates/agri-brain/";

    private final Map<String, STGroup> templateGroups = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        log.info("初始化模板服务...");
        preloadTemplates();
    }

    private void preloadTemplates() {
        String[] templateFiles = {"system_prompt.stg", "quick_advice.stg"};

        for (String templateFile : templateFiles) {
            try {
                String path = TEMPLATE_BASE_PATH + templateFile;
                STGroup group = new STGroupFile(path, "UTF-8", '<', '>');
                templateGroups.put(templateFile, group);
                log.info("加载模板成功: {}", path);
            } catch (Exception e) {
                log.error("加载模板失败: {}", templateFile, e);
            }
        }
    }

    @Override
    public String render(String templateName, Map<String, Object> attributes) {
        String templateFile = templateName + ".stg";
        STGroup group = templateGroups.get(templateFile);

        if (group == null) {
            throw new IllegalArgumentException("模板不存在: " + templateName);
        }

        ST template = group.getInstanceOf(templateName);
        if (template == null) {
            throw new IllegalArgumentException("模板实例不存在: " + templateName);
        }

        if (attributes != null) {
            for (Map.Entry<String, Object> entry : attributes.entrySet()) {
                template.add(entry.getKey(), entry.getValue());
            }
        }

        return template.render();
    }
}
