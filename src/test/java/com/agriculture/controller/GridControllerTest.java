package com.agriculture.controller;

import com.agriculture.dto.GridCreateDTO;
import com.agriculture.dto.GridUpdateDTO;
import com.agriculture.exception.BusinessException;
import com.agriculture.exception.GlobalExceptionHandler;
import com.agriculture.service.GridService;
import com.agriculture.vo.GridVO;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 网格区域模块控制器测试
 */
@ExtendWith(MockitoExtension.class)
class GridControllerTest {

    private MockMvc mockMvc;

    @Mock
    private GridService gridService;

    @InjectMocks
    private GridController gridController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private GridVO mockGridVO;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(gridController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new LocalValidatorFactoryBean())
                .build();

        mockGridVO = new GridVO();
        mockGridVO.setId("grid-001");
        mockGridVO.setLabel("A1");
        mockGridVO.setGreenhouseId("gh-001");
        mockGridVO.setPolygonCoords("[{\"x\":0,\"y\":0},{\"x\":100,\"y\":0},{\"x\":100,\"y\":100},{\"x\":0,\"y\":100}]");
        mockGridVO.setAreaM2(new BigDecimal("25.0"));
        mockGridVO.setCropType("番茄");
        mockGridVO.setCreatedAt(LocalDateTime.of(2026, 6, 1, 10, 0, 0));
    }

    // ==================== 6.1 网格列表接口 ====================

    @Nested
    @DisplayName("6.1 网格列表接口")
    class ListGrids {

        @Test
        @DisplayName("获取网格列表成功")
        void listGrids_success() throws Exception {
            when(gridService.listGrids(isNull()))
                    .thenReturn(List.of(mockGridVO));

            mockMvc.perform(get("/api/grid/list"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].id").value("grid-001"))
                    .andExpect(jsonPath("$.data[0].label").value("A1"))
                    .andExpect(jsonPath("$.data[0].cropType").value("番茄"));
        }

        @Test
        @DisplayName("按大棚ID筛选网格")
        void listGrids_filterByGreenhouse() throws Exception {
            when(gridService.listGrids(eq("gh-001")))
                    .thenReturn(List.of(mockGridVO));

            mockMvc.perform(get("/api/grid/list").param("greenhouseId", "gh-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.length()").value(1));
        }

        @Test
        @DisplayName("网格列表为空")
        void listGrids_empty() throws Exception {
            when(gridService.listGrids(isNull()))
                    .thenReturn(List.of());

            mockMvc.perform(get("/api/grid/list"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isEmpty());
        }
    }

    // ==================== 6.2 新增网格接口 ====================

    @Nested
    @DisplayName("6.2 新增网格接口")
    class CreateGrid {

        @Test
        @DisplayName("新增网格成功")
        void create_validDTO_success() throws Exception {
            GridCreateDTO dto = new GridCreateDTO();
            dto.setLabel("B1");
            dto.setGreenhouseId("gh-001");
            dto.setCropType("黄瓜");

            when(gridService.createGrid(any(GridCreateDTO.class)))
                    .thenReturn("grid-new-001");

            mockMvc.perform(post("/api/grid")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value("grid-new-001"))
                    .andExpect(jsonPath("$.message").value("网格创建成功"));
        }

        @Test
        @DisplayName("网格编号为空时返回400")
        void create_emptyLabel_returns400() throws Exception {
            GridCreateDTO dto = new GridCreateDTO();
            dto.setLabel("");
            dto.setGreenhouseId("gh-001");

            mockMvc.perform(post("/api/grid")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("网格编号不能为空"));
        }

        @Test
        @DisplayName("网格编号重复时返回错误")
        void create_duplicateLabel_returnsError() throws Exception {
            GridCreateDTO dto = new GridCreateDTO();
            dto.setLabel("A1");
            dto.setGreenhouseId("gh-001");

            when(gridService.createGrid(any(GridCreateDTO.class)))
                    .thenThrow(new BusinessException("网格编号已存在"));

            mockMvc.perform(post("/api/grid")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.message").value("网格编号已存在"));
        }
    }

    // ==================== 6.3 修改网格接口 ====================

    @Nested
    @DisplayName("6.3 修改网格接口")
    class UpdateGrid {

        @Test
        @DisplayName("修改网格成功")
        void update_validDTO_success() throws Exception {
            GridUpdateDTO dto = new GridUpdateDTO();
            dto.setLabel("A2");
            dto.setCropType("番茄");

            doNothing().when(gridService).updateGrid(eq("grid-001"), any(GridUpdateDTO.class));

            mockMvc.perform(put("/api/grid/grid-001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("网格更新成功"));
        }

        @Test
        @DisplayName("修改不存在的网格返回错误")
        void update_notExist_returnsError() throws Exception {
            GridUpdateDTO dto = new GridUpdateDTO();
            dto.setLabel("A2");

            doThrow(new BusinessException("网格不存在"))
                    .when(gridService).updateGrid(eq("not-exist"), any(GridUpdateDTO.class));

            mockMvc.perform(put("/api/grid/not-exist")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.message").value("网格不存在"));
        }

        @Test
        @DisplayName("修改编号重复返回错误")
        void update_duplicateLabel_returnsError() throws Exception {
            GridUpdateDTO dto = new GridUpdateDTO();
            dto.setLabel("B1");

            doThrow(new BusinessException("网格编号已存在"))
                    .when(gridService).updateGrid(eq("grid-001"), any(GridUpdateDTO.class));

            mockMvc.perform(put("/api/grid/grid-001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.message").value("网格编号已存在"));
        }
    }

    // ==================== 6.4 删除网格接口 ====================

    @Nested
    @DisplayName("6.4 删除网格接口")
    class DeleteGrid {

        @Test
        @DisplayName("删除网格成功")
        void delete_existingId_success() throws Exception {
            doNothing().when(gridService).deleteGrid("grid-001");

            mockMvc.perform(delete("/api/grid/grid-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("网格删除成功"));
        }

        @Test
        @DisplayName("删除不存在的网格返回错误")
        void delete_notExist_returnsError() throws Exception {
            doThrow(new BusinessException("网格不存在"))
                    .when(gridService).deleteGrid("not-exist");

            mockMvc.perform(delete("/api/grid/not-exist"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.message").value("网格不存在"));
        }
    }
}
