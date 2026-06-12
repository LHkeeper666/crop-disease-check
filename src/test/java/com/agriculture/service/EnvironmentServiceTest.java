package com.agriculture.service;

import com.agriculture.modules.environment.mapper.EnvironmentMapper;
import com.agriculture.modules.greenhouse.mapper.GreenhouseMapper;
import com.agriculture.modules.environment.dto.EnvironmentReportDTO;
import com.agriculture.modules.environment.entity.EnvironmentRecord;
import com.agriculture.modules.greenhouse.entity.Greenhouse;
import com.agriculture.common.exception.BusinessException;
import com.agriculture.modules.environment.service.impl.EnvironmentServiceImpl;
import com.agriculture.modules.environment.vo.EnvironmentCurrentVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EnvironmentService 单元测试")
class EnvironmentServiceTest {

    @Mock
    private EnvironmentMapper environmentMapper;

    @Mock
    private GreenhouseMapper greenhouseMapper;

    @InjectMocks
    private EnvironmentServiceImpl environmentService;

    private EnvironmentRecord mockRecord;
    private Greenhouse mockGreenhouse;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(environmentService, "baseMapper", environmentMapper);

        mockGreenhouse = new Greenhouse();
        mockGreenhouse.setId("gh-001");
        mockGreenhouse.setSectorId("GH-A1");
        mockGreenhouse.setCompanyId("comp-001");

        mockRecord = new EnvironmentRecord();
        mockRecord.setId("env-001");
        mockRecord.setGreenhouseId("gh-001");
        mockRecord.setCompanyId("comp-001");
        mockRecord.setAirTemp(new BigDecimal("23.6"));
        mockRecord.setSoilMoisture(new BigDecimal("65.2"));
        mockRecord.setHumidity(new BigDecimal("78.5"));
        mockRecord.setLightLevel(new BigDecimal("1230"));
        mockRecord.setCo2(new BigDecimal("420"));
        mockRecord.setSoilPh(new BigDecimal("6.8"));
        mockRecord.setEc(new BigDecimal("1.2"));
        mockRecord.setNitrogen(new BigDecimal("45"));
        mockRecord.setPhosphorus(new BigDecimal("32"));
        mockRecord.setPotassium(new BigDecimal("180"));
        mockRecord.setEnergyCurrent(new BigDecimal("55.44"));
        mockRecord.setEnergyMax(new BigDecimal("100.0"));
        mockRecord.setRecordedAt(LocalDateTime.of(2026, 6, 10, 10, 30, 0));
        mockRecord.setCreatedAt(LocalDateTime.of(2026, 6, 10, 10, 30, 0));
    }

    // ==================== 获取当前数据 ====================

    @Nested
    @DisplayName("获取当前环境数据")
    class GetCurrentData {

        @Test
        @DisplayName("有数据时返回完整快照")
        void withData_returnsSnapshot() {
            doReturn(mockRecord).when(environmentMapper).selectOne(any(), anyBoolean());
            when(greenhouseMapper.selectById("gh-001")).thenReturn(mockGreenhouse);

            EnvironmentCurrentVO result = environmentService.getCurrentData("gh-001");

            assertNotNull(result);
            assertEquals("gh-001", result.getGreenhouseId());
            assertEquals("GH-A1", result.getSectorId());
            assertNotNull(result.getEnvironment());
            assertNotNull(result.getEnvironment().getAirTemp());
            assertEquals(new BigDecimal("23.6"), result.getEnvironment().getAirTemp().getValue());
            assertEquals("°C", result.getEnvironment().getAirTemp().getUnit());
            assertEquals("normal", result.getEnvironment().getAirTemp().getStatus());
            assertNotNull(result.getGrowthMetrics());
            assertNotNull(result.getEnergy());
        }

        @Test
        @DisplayName("无数据时抛出40100异常")
        void noData_throwsException() {
            doReturn(null).when(environmentMapper).selectOne(any(), anyBoolean());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> environmentService.getCurrentData("gh-001"));
            assertEquals(40100, ex.getCode());
        }

        @Test
        @DisplayName("不传greenhouseId时取首个温室")
        void noGreenhouseId_usesFirst() {
            when(greenhouseMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(mockGreenhouse);
            doReturn(mockRecord).when(environmentMapper).selectOne(any(), anyBoolean());
            when(greenhouseMapper.selectById("gh-001")).thenReturn(mockGreenhouse);

            EnvironmentCurrentVO result = environmentService.getCurrentData(null);

            assertEquals("gh-001", result.getGreenhouseId());
        }

        @Test
        @DisplayName("无温室时抛出40100异常")
        void noGreenhouse_throwsException() {
            when(greenhouseMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> environmentService.getCurrentData(null));
            assertEquals(40100, ex.getCode());
        }

        @Test
        @DisplayName("湿度超阈值判定为warning")
        void humidityAboveThreshold_warning() {
            mockRecord.setHumidity(new BigDecimal("77.0")); // 超出75但不超10%
            doReturn(mockRecord).when(environmentMapper).selectOne(any(), anyBoolean());
            when(greenhouseMapper.selectById("gh-001")).thenReturn(mockGreenhouse);

            EnvironmentCurrentVO result = environmentService.getCurrentData("gh-001");

            assertEquals("warning", result.getEnvironment().getHumidity().getStatus());
        }

        @Test
        @DisplayName("温度严重超标判定为critical")
        void tempCritical() {
            mockRecord.setAirTemp(new BigDecimal("40.0")); // 超出35的10%以上
            doReturn(mockRecord).when(environmentMapper).selectOne(any(), anyBoolean());
            when(greenhouseMapper.selectById("gh-001")).thenReturn(mockGreenhouse);

            EnvironmentCurrentVO result = environmentService.getCurrentData("gh-001");

            assertEquals("critical", result.getEnvironment().getAirTemp().getStatus());
        }
    }

    // ==================== 历史数据查询 ====================

    @Nested
    @DisplayName("历史数据查询")
    class GetHistoryData {

        @Test
        @DisplayName("默认参数查询")
        void defaultParams_returnsPage() {
            Page<EnvironmentRecord> page = new Page<>(1, 100, 1);
            page.setRecords(List.of(mockRecord));
            when(environmentMapper.selectPage(any(IPage.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(page);

            IPage<EnvironmentRecord> result = environmentService.getHistoryData(null, null, null, null, 1, 100);

            assertEquals(1, result.getTotal());
            assertEquals("env-001", result.getRecords().get(0).getId());
        }

        @Test
        @DisplayName("按greenhouseId筛选")
        void filterByGreenhouseId() {
            Page<EnvironmentRecord> page = new Page<>(1, 100, 0);
            page.setRecords(Collections.emptyList());
            when(environmentMapper.selectPage(any(IPage.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(page);

            IPage<EnvironmentRecord> result = environmentService.getHistoryData("gh-002", null, null, null, 1, 100);

            assertEquals(0, result.getTotal());
        }
    }

    // ==================== 数据上报 ====================

    @Nested
    @DisplayName("数据上报")
    class ReportData {

        @Test
        @DisplayName("成功上报数据")
        void reportSuccess() {
            EnvironmentReportDTO dto = new EnvironmentReportDTO();
            dto.setGreenhouseId("gh-001");
            dto.setAirTemp(new BigDecimal("25.0"));

            when(greenhouseMapper.selectById("gh-001")).thenReturn(mockGreenhouse);
            doReturn(null).when(environmentMapper).selectOne(any(), anyBoolean());
            doAnswer(invocation -> {
                EnvironmentRecord entity = invocation.getArgument(0);
                entity.setId("env-new-001");
                return 1;
            }).when(environmentMapper).insert(any(EnvironmentRecord.class));

            String id = environmentService.reportData(dto);

            assertNotNull(id);
            assertEquals("env-new-001", id);
            verify(environmentMapper).insert(any(EnvironmentRecord.class));
        }

        @Test
        @DisplayName("无效greenhouseId抛出40100异常")
        void invalidGreenhouseId_throwsException() {
            EnvironmentReportDTO dto = new EnvironmentReportDTO();
            dto.setGreenhouseId("not-exist");

            when(greenhouseMapper.selectById("not-exist")).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> environmentService.reportData(dto));
            assertEquals(40100, ex.getCode());
        }

        @Test
        @DisplayName("上报频率过高抛出40102异常")
        void tooFrequent_throwsException() {
            EnvironmentReportDTO dto = new EnvironmentReportDTO();
            dto.setGreenhouseId("gh-001");
            dto.setAirTemp(new BigDecimal("25.0"));

            mockRecord.setRecordedAt(LocalDateTime.now().minusSeconds(30));
            when(greenhouseMapper.selectById("gh-001")).thenReturn(mockGreenhouse);
            doReturn(mockRecord).when(environmentMapper).selectOne(any(), anyBoolean());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> environmentService.reportData(dto));
            assertEquals(40102, ex.getCode());
        }
    }
}
