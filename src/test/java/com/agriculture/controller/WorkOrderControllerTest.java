package com.agriculture.controller;

import com.agriculture.dto.CallbackDTO;
import com.agriculture.dto.WorkOrderCreateDTO;
import com.agriculture.exception.BusinessException;
import com.agriculture.exception.GlobalExceptionHandler;
import com.agriculture.service.WorkOrderService;
import com.agriculture.vo.*;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class WorkOrderControllerTest {

    private MockMvc mockMvc;

    @Mock
    private WorkOrderService workOrderService;

    @InjectMocks
    private WorkOrderController workOrderController;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private WorkOrderVO mockWorkOrderVO;
    private WorkOrderDetailVO mockDetailVO;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(workOrderController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new LocalValidatorFactoryBean())
                .build();

        mockWorkOrderVO = new WorkOrderVO();
        mockWorkOrderVO.setId("wo-001");
        mockWorkOrderVO.setTitle("【HIGH】番茄晚疫病 工单");
        mockWorkOrderVO.setSeverity("HIGH");
        mockWorkOrderVO.setStatus("PENDING");
        mockWorkOrderVO.setType("DISEASE");
        mockWorkOrderVO.setGridLabel("A1");
        mockWorkOrderVO.setPestName("番茄晚疫病");
        mockWorkOrderVO.setConfidence(new BigDecimal("0.92"));
        mockWorkOrderVO.setImageUrl("/images/report/xxx.jpg");
        mockWorkOrderVO.setAssignedToName("李专家");
        mockWorkOrderVO.setCreatedAt(LocalDateTime.of(2026, 6, 9, 10, 30, 0));
        mockWorkOrderVO.setUpdatedAt(LocalDateTime.of(2026, 6, 9, 10, 30, 0));

        mockDetailVO = new WorkOrderDetailVO();
        org.springframework.beans.BeanUtils.copyProperties(mockWorkOrderVO, mockDetailVO);
        mockDetailVO.setInferenceId("inf-001");
        mockDetailVO.setExpertComment(null);
        mockDetailVO.setStatusHistory(List.of(
                createHistory("PENDING", LocalDateTime.of(2026, 6, 9, 10, 30, 0), "系统"),
                createHistory("PROCESSING", LocalDateTime.of(2026, 6, 9, 10, 35, 0), "李专家")
        ));
    }

    private StatusHistoryVO createHistory(String status, LocalDateTime time, String operator) {
        StatusHistoryVO vo = new StatusHistoryVO();
        vo.setStatus(status);
        vo.setCreatedAt(time);
        vo.setOperator(operator);
        return vo;
    }

    // ==================== 7.1 工单列表接口 ====================

    @Nested
    @DisplayName("7.1 工单列表接口")
    class ListWorkOrders {

        @Test
        @DisplayName("无条件查询工单列表")
        void listWorkOrders_noParams_returnsPage() throws Exception {
            Page<WorkOrderVO> page = new Page<>(1, 20, 1);
            page.setRecords(List.of(mockWorkOrderVO));
            when(workOrderService.listWorkOrders(isNull(), isNull(), isNull(), isNull(), eq(1), eq(20)))
                    .thenReturn(page);

            mockMvc.perform(get("/workorder/list"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.records[0].id").value("wo-001"))
                    .andExpect(jsonPath("$.data.records[0].title").value("【HIGH】番茄晚疫病 工单"))
                    .andExpect(jsonPath("$.data.records[0].type").value("DISEASE"))
                    .andExpect(jsonPath("$.data.records[0].gridLabel").value("A1"))
                    .andExpect(jsonPath("$.data.records[0].pestName").value("番茄晚疫病"))
                    .andExpect(jsonPath("$.data.records[0].confidence").value(0.92))
                    .andExpect(jsonPath("$.data.total").value(1));
        }

        @Test
        @DisplayName("按状态筛选")
        void listWorkOrders_filterByStatus() throws Exception {
            Page<WorkOrderVO> page = new Page<>(1, 20, 0);
            page.setRecords(List.of());
            when(workOrderService.listWorkOrders(eq("PENDING"), isNull(), isNull(), isNull(), eq(1), eq(20)))
                    .thenReturn(page);

            mockMvc.perform(get("/workorder/list").param("status", "PENDING"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.records").isEmpty());
        }

        @Test
        @DisplayName("按严重程度筛选")
        void listWorkOrders_filterBySeverity() throws Exception {
            Page<WorkOrderVO> page = new Page<>(1, 20, 1);
            page.setRecords(List.of(mockWorkOrderVO));
            when(workOrderService.listWorkOrders(isNull(), eq("CRITICAL"), isNull(), isNull(), eq(1), eq(20)))
                    .thenReturn(page);

            mockMvc.perform(get("/workorder/list").param("severity", "CRITICAL"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("分页查询")
        void listWorkOrders_withPagination() throws Exception {
            Page<WorkOrderVO> page = new Page<>(2, 10, 25);
            page.setRecords(List.of(mockWorkOrderVO));
            when(workOrderService.listWorkOrders(isNull(), isNull(), isNull(), isNull(), eq(2), eq(10)))
                    .thenReturn(page);

            mockMvc.perform(get("/workorder/list").param("page", "2").param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.current").value(2))
                    .andExpect(jsonPath("$.data.size").value(10))
                    .andExpect(jsonPath("$.data.total").value(25));
        }
    }

    // ==================== 7.2 工单详情接口 ====================

    @Nested
    @DisplayName("7.2 工单详情接口")
    class GetWorkOrderDetail {

        @Test
        @DisplayName("查询存在的工单")
        void getDetail_existingId_returnsDetail() throws Exception {
            when(workOrderService.getWorkOrderDetail("wo-001")).thenReturn(mockDetailVO);

            mockMvc.perform(get("/workorder/wo-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.id").value("wo-001"))
                    .andExpect(jsonPath("$.data.inferenceId").value("inf-001"))
                    .andExpect(jsonPath("$.data.statusHistory").isArray())
                    .andExpect(jsonPath("$.data.statusHistory[0].status").value("PENDING"))
                    .andExpect(jsonPath("$.data.statusHistory[0].operator").value("系统"))
                    .andExpect(jsonPath("$.data.statusHistory[1].status").value("PROCESSING"))
                    .andExpect(jsonPath("$.data.statusHistory[1].operator").value("李专家"));
        }

        @Test
        @DisplayName("查询不存在的工单返回404")
        void getDetail_nonExistingId_returns404() throws Exception {
            when(workOrderService.getWorkOrderDetail("not-exist"))
                    .thenThrow(new BusinessException(404, "工单不存在"));

            mockMvc.perform(get("/workorder/not-exist"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.message").value("工单不存在"));
        }
    }

    // ==================== 7.3 手动创建工单接口 ====================

    @Nested
    @DisplayName("7.3 手动创建工单接口")
    class CreateWorkOrder {

        @Test
        @DisplayName("创建工单成功")
        void create_validDTO_returnsId() throws Exception {
            WorkOrderCreateDTO dto = new WorkOrderCreateDTO();
            dto.setInferenceId("inf-001");
            dto.setSeverity("HIGH");
            dto.setAssignedTo("user-expert-001");

            when(workOrderService.createWorkOrder(any(WorkOrderCreateDTO.class), anyString(), anyString()))
                    .thenReturn("wo-new-001");

            mockMvc.perform(post("/workorder/create")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value("wo-new-001"))
                    .andExpect(jsonPath("$.message").value("工单创建成功"));
        }

        @Test
        @DisplayName("severity为空时返回400")
        void create_emptySeverity_returns400() throws Exception {
            WorkOrderCreateDTO dto = new WorkOrderCreateDTO();
            dto.setInferenceId("inf-001");
            dto.setSeverity("");

            mockMvc.perform(post("/workorder/create")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("严重程度不能为空"));
        }

        @Test
        @DisplayName("关联识别记录不存在时返回错误")
        void create_invalidInferenceId_returnsError() throws Exception {
            WorkOrderCreateDTO dto = new WorkOrderCreateDTO();
            dto.setInferenceId("not-exist");
            dto.setSeverity("HIGH");

            when(workOrderService.createWorkOrder(any(WorkOrderCreateDTO.class), anyString(), anyString()))
                    .thenThrow(new BusinessException("关联的识别记录不存在"));

            mockMvc.perform(post("/workorder/create")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.message").value("关联的识别记录不存在"));
        }
    }

    // ==================== 7.4 Token 回调接口 ====================

    @Nested
    @DisplayName("7.4 Token 回调接口")
    class HandleCallback {

        @Test
        @DisplayName("专家确认工单")
        void callback_confirm_success() throws Exception {
            CallbackDTO dto = new CallbackDTO();
            dto.setToken("abc123token");
            dto.setAction("CONFIRM");
            dto.setComment("已确认，建议立即喷药");

            CallbackResponseVO response = new CallbackResponseVO();
            response.setWorkorderId("wo-001");
            response.setNewStatus("DONE");

            when(workOrderService.handleCallback(any(CallbackDTO.class))).thenReturn(response);

            mockMvc.perform(post("/workorder/callback")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.workorderId").value("wo-001"))
                    .andExpect(jsonPath("$.data.newStatus").value("DONE"));
        }

        @Test
        @DisplayName("专家忽略工单")
        void callback_ignore_success() throws Exception {
            CallbackDTO dto = new CallbackDTO();
            dto.setToken("abc123token");
            dto.setAction("IGNORE");
            dto.setComment("误报，忽略");

            CallbackResponseVO response = new CallbackResponseVO();
            response.setWorkorderId("wo-001");
            response.setNewStatus("IGNORED");

            when(workOrderService.handleCallback(any(CallbackDTO.class))).thenReturn(response);

            mockMvc.perform(post("/workorder/callback")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.newStatus").value("IGNORED"));
        }

        @Test
        @DisplayName("Token无效返回40060")
        void callback_invalidToken_returns40060() throws Exception {
            CallbackDTO dto = new CallbackDTO();
            dto.setToken("invalid");
            dto.setAction("CONFIRM");

            when(workOrderService.handleCallback(any(CallbackDTO.class)))
                    .thenThrow(new BusinessException(40060, "Token 无效"));

            mockMvc.perform(post("/workorder/callback")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40060))
                    .andExpect(jsonPath("$.message").value("Token 无效"));
        }

        @Test
        @DisplayName("Token已过期返回40061")
        void callback_expiredToken_returns40061() throws Exception {
            CallbackDTO dto = new CallbackDTO();
            dto.setToken("expired");
            dto.setAction("CONFIRM");

            when(workOrderService.handleCallback(any(CallbackDTO.class)))
                    .thenThrow(new BusinessException(40061, "Token 已过期"));

            mockMvc.perform(post("/workorder/callback")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40061))
                    .andExpect(jsonPath("$.message").value("Token 已过期"));
        }

        @Test
        @DisplayName("Token已使用返回40062")
        void callback_usedToken_returns40062() throws Exception {
            CallbackDTO dto = new CallbackDTO();
            dto.setToken("used");
            dto.setAction("CONFIRM");

            when(workOrderService.handleCallback(any(CallbackDTO.class)))
                    .thenThrow(new BusinessException(40062, "Token 已使用，该工单已被处理"));

            mockMvc.perform(post("/workorder/callback")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40062))
                    .andExpect(jsonPath("$.message").value("Token 已使用，该工单已被处理"));
        }

        @Test
        @DisplayName("action为空时返回400")
        void callback_emptyAction_returns400() throws Exception {
            CallbackDTO dto = new CallbackDTO();
            dto.setToken("abc123");
            dto.setAction("");

            mockMvc.perform(post("/workorder/callback")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("操作类型不能为空"));
        }
    }
}
