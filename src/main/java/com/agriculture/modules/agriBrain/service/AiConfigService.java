package com.agriculture.modules.agriBrain.service;

import com.agriculture.modules.agriBrain.entity.AiConfig;
import com.baomidou.mybatisplus.extension.service.IService;

public interface AiConfigService extends IService<AiConfig> {

    String getConfigValue(String configKey);

    void setConfigValue(String configKey, String configValue);
}
