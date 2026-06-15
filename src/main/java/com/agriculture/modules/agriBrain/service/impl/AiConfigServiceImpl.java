package com.agriculture.modules.agriBrain.service.impl;

import com.agriculture.modules.agriBrain.entity.AiConfig;
import com.agriculture.modules.agriBrain.mapper.AiConfigMapper;
import com.agriculture.modules.agriBrain.service.AiConfigService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AiConfigServiceImpl extends ServiceImpl<AiConfigMapper, AiConfig> implements AiConfigService {

    @Override
    public String getConfigValue(String configKey) {
        AiConfig config = getOne(new LambdaQueryWrapper<AiConfig>()
                .eq(AiConfig::getConfigKey, configKey));
        return config != null ? config.getConfigValue() : null;
    }

    @Override
    public void setConfigValue(String configKey, String configValue) {
        AiConfig config = getOne(new LambdaQueryWrapper<AiConfig>()
                .eq(AiConfig::getConfigKey, configKey));
        if (config != null) {
            config.setConfigValue(configValue);
            config.setUpdatedAt(LocalDateTime.now());
            updateById(config);
        } else {
            config = new AiConfig();
            config.setId(UUID.randomUUID().toString());
            config.setConfigKey(configKey);
            config.setConfigValue(configValue);
            config.setUpdatedAt(LocalDateTime.now());
            save(config);
        }
    }
}
