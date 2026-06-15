package com.agriculture.service;

import com.agriculture.modules.agriBrain.entity.AiConfig;
import com.agriculture.modules.agriBrain.mapper.AiConfigMapper;
import com.agriculture.modules.agriBrain.service.impl.AiConfigServiceImpl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiConfigServiceTest {

    @Mock
    private AiConfigMapper aiConfigMapper;

    @InjectMocks
    private AiConfigServiceImpl aiConfigService;

    @BeforeEach
    void setUp() throws Exception {
        // ServiceImpl 的 baseMapper 是 protected 字段，需要通过反射注入
        Field baseMapperField = ServiceImpl.class.getDeclaredField("baseMapper");
        baseMapperField.setAccessible(true);
        baseMapperField.set(aiConfigService, aiConfigMapper);
    }

    private void mockSelectOne(AiConfig result) {
        // mock 单参数 selectOne(Wrapper)
        doReturn(result).when(aiConfigMapper).selectOne(any(LambdaQueryWrapper.class));
        // mock 双参数 selectOne(Wrapper, Boolean)，ServiceImpl.getOne() 调用此版本
        doReturn(result).when(aiConfigMapper).selectOne(any(LambdaQueryWrapper.class), anyBoolean());
    }

    @Nested
    @DisplayName("getConfigValue 方法")
    class GetConfigValue {

        @Test
        @DisplayName("配置存在时返回值")
        void getConfigValue_exists_returnsValue() {
            AiConfig config = new AiConfig();
            config.setConfigKey("apiKey");
            config.setConfigValue("sk-test-key");
            mockSelectOne(config);

            String result = aiConfigService.getConfigValue("apiKey");

            assertEquals("sk-test-key", result);
        }

        @Test
        @DisplayName("配置不存在时返回 null")
        void getConfigValue_notExists_returnsNull() {
            mockSelectOne(null);

            String result = aiConfigService.getConfigValue("apiKey");

            assertNull(result);
        }
    }

    @Nested
    @DisplayName("setConfigValue 方法")
    class SetConfigValue {

        @Test
        @DisplayName("配置存在时更新")
        void setConfigValue_exists_updates() {
            AiConfig existingConfig = new AiConfig();
            existingConfig.setId("config-001");
            existingConfig.setConfigKey("apiKey");
            existingConfig.setConfigValue("old-key");
            mockSelectOne(existingConfig);
            doReturn(1).when(aiConfigMapper).updateById(any(AiConfig.class));

            aiConfigService.setConfigValue("apiKey", "new-key");

            ArgumentCaptor<AiConfig> captor = ArgumentCaptor.forClass(AiConfig.class);
            verify(aiConfigMapper).updateById(captor.capture());
            AiConfig captured = captor.getValue();
            assertEquals("config-001", captured.getId());
            assertEquals("new-key", captured.getConfigValue());
        }

        @Test
        @DisplayName("配置不存在时创建")
        void setConfigValue_notExists_creates() {
            mockSelectOne(null);
            doReturn(1).when(aiConfigMapper).insert(any(AiConfig.class));

            aiConfigService.setConfigValue("apiKey", "new-key");

            ArgumentCaptor<AiConfig> captor = ArgumentCaptor.forClass(AiConfig.class);
            verify(aiConfigMapper).insert(captor.capture());
            AiConfig captured = captor.getValue();
            assertEquals("apiKey", captured.getConfigKey());
            assertEquals("new-key", captured.getConfigValue());
        }
    }
}
