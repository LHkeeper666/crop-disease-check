package com.agriculture.controller;

import com.agriculture.dto.GreenhouseDTO;
import com.agriculture.entity.Greenhouse;
import com.agriculture.exception.BusinessException;
import com.agriculture.exception.GlobalExceptionHandler;
import com.agriculture.service.GreenhouseService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GreenhouseController 集成测试")
class GreenhouseControllerTest {

    private MockMvc mockMvc;

    @Mock
    private GreenhouseService greenhouseService;

    @InjectMocks
    private GreenhouseController greenhouseController;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private Greenhouse mockGreenhouse;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(greenhouseController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new LocalValidatorFactoryBean())
                .build();

        mockGreenhouse = new Greenhouse();
        mockGreenhouse.setId("gh-001");
        mockGreenhouse.setSectorId("GH-A1");
        mockGreenhouse.setCropSpecies("Solanum lycopersicum");
        mockGreenhouse.setPlantingDate(LocalDate.of(2026, 3, 15));
        mockGreenhouse.setLocation("34.2614N, 108.9423E");
        mockGreenhouse.setArea(new BigDecimal("2400.00"));
        mockGreenhouse.setStatus("ACTIVE");
        mockGreenhouse.setCompanyId("comp-001");
        mockGreenhouse.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0, 0));
    }

    // ==================== 温室列表 ====================

    @Nested
    @DisplayName("温室列表接口")
    class ListGreenhouses {

        @Test
        @DisplayName("无条件查询温室列表")
        void listNoParams_returnsPage() throws Exception {
            Page<Greenhouse> page = new Page<>(1, 20, 1);
            page.setRecords(List.of(mockGreenhouse));
            when(greenhouseService.listGreenhouses(isNull(), isNull(), eq(1), eq(20))).thenReturn(page);

            mockMvc.perform(get("/greenhouse/list"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.records[0].id").value("gh-001"))
                    .andExpect(jsonPath("$.data.records[0].sectorId").value("GH-A1"))
                    .andExpect(jsonPath("$.data.total").value(1));
        }

        @Test
        @DisplayName("按状态筛选")
        void listByStatus() throws Exception {
            Page<Greenhouse> page = new Page<>(1, 20, 0);
            page.setRecords(List.of());
            when(greenhouseService.listGreenhouses(eq("ACTIVE"), isNull(), eq(1), eq(20))).thenReturn(page);

            mockMvc.perform(get("/greenhouse/list").param("status", "ACTIVE"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.records").isEmpty());
        }

        @Test
        @DisplayName("关键词搜索")
        void listByKeyword() throws Exception {
            Page<Greenhouse> page = new Page<>(1, 20, 1);
            page.setRecords(List.of(mockGreenhouse));
            when(greenhouseService.listGreenhouses(isNull(), eq("GH-A"), eq(1), eq(20))).thenReturn(page);

            mockMvc.perform(get("/greenhouse/list").param("keyword", "GH-A"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.records[0].sectorId").value("GH-A1"));
        }
    }

    // ==================== 温室详情 ====================

    @Nested
    @DisplayName("温室详情接口")
    class GetDetail {

        @Test
        @DisplayName("查询存在的温室")
        void detailExists() throws Exception {
            mockGreenhouse.setGridCount(9);
            mockGreenhouse.setCameraCount(2);
            when(greenhouseService.getGreenhouseDetail("gh-001")).thenReturn(mockGreenhouse);

            mockMvc.perform(get("/greenhouse/gh-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.id").value("gh-001"))
                    .andExpect(jsonPath("$.data.sectorId").value("GH-A1"))
                    .andExpect(jsonPath("$.data.gridCount").value(9))
                    .andExpect(jsonPath("$.data.cameraCount").value(2));
        }

        @Test
        @DisplayName("查询不存在的温室返回40112")
        void detailNotExists() throws Exception {
            when(greenhouseService.getGreenhouseDetail("not-exist"))
                    .thenThrow(new BusinessException(40112, "温室不存在"));

            mockMvc.perform(get("/greenhouse/not-exist"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40112))
                    .andExpect(jsonPath("$.message").value("温室不存在"));
        }
    }

    // ==================== 新增温室 ====================

    @Nested
    @DisplayName("新增温室接口")
    class CreateGreenhouse {

        @Test
        @DisplayName("成功新增温室")
        void createSuccess() throws Exception {
            GreenhouseDTO dto = new GreenhouseDTO();
            dto.setSectorId("GH-A2");
            dto.setCropSpecies("番茄");

            when(greenhouseService.createGreenhouse(any(GreenhouseDTO.class), anyString()))
                    .thenReturn("gh-new-001");

            mockMvc.perform(post("/greenhouse")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value("gh-new-001"))
                    .andExpect(jsonPath("$.message").value("温室创建成功"));
        }

        @Test
        @DisplayName("sectorId为空返回400")
        void createEmptySectorId_returns400() throws Exception {
            GreenhouseDTO dto = new GreenhouseDTO();
            dto.setSectorId("");

            mockMvc.perform(post("/greenhouse")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("区域编号已存在返回40110")
        void createDuplicateSectorId_returns40110() throws Exception {
            GreenhouseDTO dto = new GreenhouseDTO();
            dto.setSectorId("GH-A1");

            when(greenhouseService.createGreenhouse(any(GreenhouseDTO.class), anyString()))
                    .thenThrow(new BusinessException(40110, "区域编号已存在"));

            mockMvc.perform(post("/greenhouse")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40110))
                    .andExpect(jsonPath("$.message").value("区域编号已存在"));
        }
    }

    // ==================== 修改温室 ====================

    @Nested
    @DisplayName("修改温室接口")
    class UpdateGreenhouse {

        @Test
        @DisplayName("成功修改温室")
        void updateSuccess() throws Exception {
            GreenhouseDTO dto = new GreenhouseDTO();
            dto.setSectorId("GH-A1");
            dto.setCropSpecies("玉米");

            mockMvc.perform(put("/greenhouse/gh-001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("修改不存在的温室返回40112")
        void updateNotExists_returns40112() throws Exception {
            GreenhouseDTO dto = new GreenhouseDTO();
            dto.setSectorId("GH-A1");
            dto.setCropSpecies("玉米");

            doThrow(new BusinessException(40112, "温室不存在"))
                    .when(greenhouseService).updateGreenhouse(eq("not-exist"), any(GreenhouseDTO.class));

            mockMvc.perform(put("/greenhouse/not-exist")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40112));
        }
    }

    // ==================== 删除温室 ====================

    @Nested
    @DisplayName("删除温室接口")
    class DeleteGreenhouse {

        @Test
        @DisplayName("成功删除温室")
        void deleteSuccess() throws Exception {
            mockMvc.perform(delete("/greenhouse/gh-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("温室删除成功"));
        }

        @Test
        @DisplayName("删除不存在的温室返回40112")
        void deleteNotExists_returns40112() throws Exception {
            doThrow(new BusinessException(40112, "温室不存在"))
                    .when(greenhouseService).deleteGreenhouse("not-exist");

            mockMvc.perform(delete("/greenhouse/not-exist"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40112));
        }

        @Test
        @DisplayName("删除有关联网格的温室返回40113")
        void deleteWithGrids_returns40113() throws Exception {
            doThrow(new BusinessException(40113, "温室下存在关联网格，无法删除"))
                    .when(greenhouseService).deleteGreenhouse("gh-001");

            mockMvc.perform(delete("/greenhouse/gh-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40113))
                    .andExpect(jsonPath("$.message").value("温室下存在关联网格，无法删除"));
        }
    }
}
