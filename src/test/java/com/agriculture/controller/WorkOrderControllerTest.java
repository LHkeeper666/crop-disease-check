package com.agriculture.controller;

import com.agriculture.modules.workorder.controller.WorkOrderController;
import com.agriculture.modules.workorder.dto.*;
import com.agriculture.common.exception.BusinessException;
import com.agriculture.common.exception.GlobalExceptionHandler;
import com.agriculture.common.service.EmailService;
import com.agriculture.modules.workorder.service.WorkOrderService;
import com.agriculture.modules.workorder.vo.*;
import com.agriculture.modules.user.entity.SysUser;
import com.agriculture.modules.user.mapper.SysUserMapper;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class WorkOrderControllerTest {

    private MockMvc mockMvc;

    @Mock
    private WorkOrderService workOrderService;

    @Mock
    private SysUserMapper sysUserMapper;

    @Mock
    private EmailService emailService;

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
        mockWorkOrderVO.setId(1L);
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

    /**
     * 构造已登录用户 mock：设置 request.attribute("userId") 和 SysUserMapper 返回
     */
    private SysUser mockCurrentUser() {
        SysUser user = new SysUser();
        user.setId("u-001");
        user.setName("系统管理员");
        user.setRole("ADMIN");
        user.setCompanyId("company-001");
        when(sysUserMapper.selectById("u-001")).thenReturn(user);
        return user;
    }

    // ==================== 7.1 工单列表接口 ====================

    @Nested
    @DisplayName("7.1 工单列表接口")
    class ListWorkOrders {

        @Test
        @DisplayName("无条件查询工单列表")
        void listWorkOrders_noParams_returnsPage() throws Exception {
            mockCurrentUser();
            Page<WorkOrderVO> page = new Page<>(1, 20, 1);
            page.setRecords(List.of(mockWorkOrderVO));
            when(workOrderService.listWorkOrders(isNull(), isNull(), isNull(), isNull(), eq(1), eq(20), eq("company-001"), isNull()))
                    .thenReturn(page);

            mockMvc.perform(get("/workorder/list")
                            .requestAttr("userId", "u-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.records[0].id").value(1))
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
            mockCurrentUser();
            Page<WorkOrderVO> page = new Page<>(1, 20, 0);
            page.setRecords(List.of());
            when(workOrderService.listWorkOrders(eq("PENDING"), isNull(), isNull(), isNull(), eq(1), eq(20), eq("company-001"), isNull()))
                    .thenReturn(page);

            mockMvc.perform(get("/workorder/list")
                            .requestAttr("userId", "u-001")
                            .param("status", "PENDING"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.records").isEmpty());
        }

        @Test
        @DisplayName("按严重程度筛选")
        void listWorkOrders_filterBySeverity() throws Exception {
            mockCurrentUser();
            Page<WorkOrderVO> page = new Page<>(1, 20, 1);
            page.setRecords(List.of(mockWorkOrderVO));
            when(workOrderService.listWorkOrders(isNull(), eq("CRITICAL"), isNull(), isNull(), eq(1), eq(20), eq("company-001"), isNull()))
                    .thenReturn(page);

            mockMvc.perform(get("/workorder/list")
                            .requestAttr("userId", "u-001")
                            .param("severity", "CRITICAL"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("分页查询")
        void listWorkOrders_withPagination() throws Exception {
            mockCurrentUser();
            Page<WorkOrderVO> page = new Page<>(2, 10, 25);
            page.setRecords(List.of(mockWorkOrderVO));
            when(workOrderService.listWorkOrders(isNull(), isNull(), isNull(), isNull(), eq(2), eq(10), eq("company-001"), isNull()))
                    .thenReturn(page);

            mockMvc.perform(get("/workorder/list")
                            .requestAttr("userId", "u-001")
                            .param("page", "2")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.current").value(2))
                    .andExpect(jsonPath("$.data.size").value(10))
                    .andExpect(jsonPath("$.data.total").value(25));
        }

        @Test
        @DisplayName("专家角色只看自己负责的工单")
        void listWorkOrders_expertRole_filtersByAssignedTo() throws Exception {
            // 专家用户
            SysUser expert = new SysUser();
            expert.setId("u-002");
            expert.setName("李专家");
            expert.setRole("EXPERT");
            expert.setCompanyId("company-001");
            when(sysUserMapper.selectById("u-002")).thenReturn(expert);

            Page<WorkOrderVO> page = new Page<>(1, 20, 1);
            page.setRecords(List.of(mockWorkOrderVO));
            when(workOrderService.listWorkOrders(isNull(), isNull(), isNull(), isNull(), eq(1), eq(20), eq("company-001"), eq("u-002")))
                    .thenReturn(page);

            mockMvc.perform(get("/workorder/list")
                            .requestAttr("userId", "u-002"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.total").value(1));
        }
    }

    // ==================== 7.2 工单详情接口 ====================

    @Nested
    @DisplayName("7.2 工单详情接口")
    class GetWorkOrderDetail {

        @Test
        @DisplayName("查询存在的工单")
        void getDetail_existingId_returnsDetail() throws Exception {
            when(workOrderService.getWorkOrderDetail(1L)).thenReturn(mockDetailVO);

            mockMvc.perform(get("/workorder/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.id").value(1))
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
            when(workOrderService.getWorkOrderDetail(999L))
                    .thenThrow(new BusinessException(404, "工单不存在"));

            mockMvc.perform(get("/workorder/999"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.message").value("工单不存在"));
        }
    }

    // ==================== 7.3 创建工单接口 ====================

    @Nested
    @DisplayName("7.3 创建工单接口")
    class CreateWorkOrder {

        @Test
        @DisplayName("基于推理记录创建工单成功")
        void create_validDTO_returnsId() throws Exception {
            mockCurrentUser();
            WorkOrderCreateDTO dto = new WorkOrderCreateDTO();
            dto.setInferenceId("inf-001");
            dto.setSeverity("HIGH");
            dto.setAssignedTo("user-expert-001");

            when(workOrderService.createWorkOrder(any(WorkOrderCreateDTO.class), eq("u-001"), eq("系统管理员"), eq("company-001")))
                    .thenReturn(1L);

            mockMvc.perform(post("/workorder/create")
                            .requestAttr("userId", "u-001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value(1))
                    .andExpect(jsonPath("$.message").value("工单创建成功"));
        }

        @Test
        @DisplayName("手动创建工单成功")
        void createManual_validDTO_returnsId() throws Exception {
            mockCurrentUser();
            WorkOrderManualCreateDTO dto = new WorkOrderManualCreateDTO();
            dto.setTitle("【紧急】Grid-B3 红蜘蛛 工单");
            dto.setSeverity("CRITICAL");
            dto.setType("pest");
            dto.setGridLabel("B3");
            dto.setPestName("红蜘蛛");
            dto.setConfidence(0.95);

            when(workOrderService.createManualWorkOrder(any(WorkOrderManualCreateDTO.class), eq("u-001"), eq("系统管理员"), eq("company-001")))
                    .thenReturn(2L);

            mockMvc.perform(post("/workorder/create-manual")
                            .requestAttr("userId", "u-001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value(2))
                    .andExpect(jsonPath("$.message").value("工单创建成功"));
        }

        @Test
        @DisplayName("severity为空时返回400")
        void create_emptySeverity_returns400() throws Exception {
            WorkOrderCreateDTO dto = new WorkOrderCreateDTO();
            dto.setInferenceId("inf-001");
            dto.setSeverity("");

            mockMvc.perform(post("/workorder/create")
                            .requestAttr("userId", "u-001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("严重程度不能为空"));
        }

        @Test
        @DisplayName("关联识别记录不存在时返回错误")
        void create_invalidInferenceId_returnsError() throws Exception {
            mockCurrentUser();
            WorkOrderCreateDTO dto = new WorkOrderCreateDTO();
            dto.setInferenceId("not-exist");
            dto.setSeverity("HIGH");

            when(workOrderService.createWorkOrder(any(WorkOrderCreateDTO.class), eq("u-001"), eq("系统管理员"), eq("company-001")))
                    .thenThrow(new BusinessException("关联的识别记录不存在"));

            mockMvc.perform(post("/workorder/create")
                            .requestAttr("userId", "u-001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.message").value("关联的识别记录不存在"));
        }
    }

    // ==================== 7.4 状态更新接口 ====================

    @Nested
    @DisplayName("7.4 状态更新接口")
    class UpdateStatus {

        @Test
        @DisplayName("确认处理工单")
        void updateStatus_pendingToProcessing_success() throws Exception {
            mockCurrentUser();
            StatusUpdateDTO dto = new StatusUpdateDTO();
            dto.setStatus("PROCESSING");

            mockMvc.perform(put("/workorder/1/status")
                            .requestAttr("userId", "u-001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("状态更新成功"));
        }

        @Test
        @DisplayName("标记完成工单")
        void updateStatus_processingToDone_success() throws Exception {
            mockCurrentUser();
            StatusUpdateDTO dto = new StatusUpdateDTO();
            dto.setStatus("DONE");
            dto.setComment("已处理完毕");

            mockMvc.perform(put("/workorder/1/status")
                            .requestAttr("userId", "u-001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("非法状态变更返回错误")
        void updateStatus_invalidTransition_returnsError() throws Exception {
            mockCurrentUser();
            StatusUpdateDTO dto = new StatusUpdateDTO();
            dto.setStatus("DONE");

            org.mockito.Mockito.doThrow(new BusinessException("非法的状态变更: PENDING -> DONE"))
                    .when(workOrderService).updateStatus(eq(1L), eq("DONE"), isNull(), eq("u-001"), eq("系统管理员"));

            mockMvc.perform(put("/workorder/1/status")
                            .requestAttr("userId", "u-001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.message").value("非法的状态变更: PENDING -> DONE"));
        }
    }

    // ==================== 7.5 严重程度更新接口 ====================

    @Nested
    @DisplayName("7.5 严重程度更新接口")
    class UpdateSeverity {

        @Test
        @DisplayName("升级严重程度")
        void updateSeverity_escalate_success() throws Exception {
            SeverityUpdateDTO dto = new SeverityUpdateDTO();
            dto.setSeverity("CRITICAL");

            mockMvc.perform(put("/workorder/1/severity")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("严重程度更新成功"));
        }

        @Test
        @DisplayName("severity为空时返回400")
        void updateSeverity_empty_returns400() throws Exception {
            SeverityUpdateDTO dto = new SeverityUpdateDTO();
            dto.setSeverity("");

            mockMvc.perform(put("/workorder/1/severity")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
        }
    }

    // ==================== 7.6 删除工单接口 ====================

    @Nested
    @DisplayName("7.6 删除工单接口")
    class DeleteWorkOrder {

        @Test
        @DisplayName("删除存在的工单")
        void delete_existingId_success() throws Exception {
            mockMvc.perform(delete("/workorder/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("工单已删除"));
        }

        @Test
        @DisplayName("删除不存在的工单返回404")
        void delete_nonExistingId_returns404() throws Exception {
            org.mockito.Mockito.doThrow(new BusinessException(404, "工单不存在"))
                    .when(workOrderService).deleteWorkOrder(999L);

            mockMvc.perform(delete("/workorder/999"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.message").value("工单不存在"));
        }
    }

    // ==================== 7.7 Token 回调接口 ====================

    @Nested
    @DisplayName("7.7 Token 回调接口")
    class HandleCallback {

        @Test
        @DisplayName("专家确认工单")
        void callback_confirm_success() throws Exception {
            CallbackDTO dto = new CallbackDTO();
            dto.setToken("abc123token");
            dto.setAction("CONFIRM");
            dto.setComment("已确认，建议立即喷药");

            CallbackResponseVO response = new CallbackResponseVO();
            response.setWorkorderId(1L);
            response.setNewStatus("DONE");

            when(workOrderService.handleCallback(any(CallbackDTO.class))).thenReturn(response);

            mockMvc.perform(post("/workorder/callback")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.workorderId").value(1))
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
            response.setWorkorderId(1L);
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

    // ==================== 7.8 邮件预览接口 ====================

    @Nested
    @DisplayName("7.8 邮件预览接口")
    class PreviewEmail {

        @Test
        @DisplayName("正常返回邮件预览数据")
        void previewEmail_existingWorkOrder_returnsPreview() throws Exception {
            EmailPreviewVO preview = new EmailPreviewVO();
            preview.setToUserId("expert-001");
            preview.setToName("刘专家");
            preview.setToEmail("expert@test.com");
            preview.setSubject("【农作物疾病检测系统】工单通知 - 番茄晚疫病工单");
            preview.setContent("尊敬的专家：\n\n您有一条新的工单通知。");

            when(workOrderService.previewEmail(1L)).thenReturn(preview);

            mockMvc.perform(post("/workorder/1/preview-email"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.toUserId").value("expert-001"))
                    .andExpect(jsonPath("$.data.toName").value("刘专家"))
                    .andExpect(jsonPath("$.data.toEmail").value("expert@test.com"))
                    .andExpect(jsonPath("$.data.subject").value("【农作物疾病检测系统】工单通知 - 番茄晚疫病工单"))
                    .andExpect(jsonPath("$.data.content").value("尊敬的专家：\n\n您有一条新的工单通知。"));
        }

        @Test
        @DisplayName("工单不存在返回错误")
        void previewEmail_nonExistingWorkOrder_returnsError() throws Exception {
            when(workOrderService.previewEmail(999L))
                    .thenThrow(new BusinessException(404, "工单不存在"));

            mockMvc.perform(post("/workorder/999/preview-email"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("工单不存在"));
        }

        @Test
        @DisplayName("未指定负责人返回错误")
        void previewEmail_noAssignedTo_returnsError() throws Exception {
            when(workOrderService.previewEmail(1L))
                    .thenThrow(new BusinessException("该工单未指定负责人，请先指定负责人后再发送邮件"));

            mockMvc.perform(post("/workorder/1/preview-email"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("该工单未指定负责人，请先指定负责人后再发送邮件"));
        }

        @Test
        @DisplayName("专家无邮箱返回错误")
        void previewEmail_expertNoEmail_returnsError() throws Exception {
            when(workOrderService.previewEmail(1L))
                    .thenThrow(new BusinessException("该专家未配置邮箱地址"));

            mockMvc.perform(post("/workorder/1/preview-email"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("该专家未配置邮箱地址"));
        }
    }

    // ==================== 7.9 邮件发送接口 ====================

    @Nested
    @DisplayName("7.9 邮件发送接口")
    class SendEmail {

        @Test
        @DisplayName("使用前端传入的 subject 和 content 发送成功")
        void sendEmail_withSubjectAndContent_success() throws Exception {
            mockCurrentUser();
            SysUser expert = new SysUser();
            expert.setId("expert-001");
            expert.setName("刘专家");
            expert.setEmail("expert@test.com");
            when(sysUserMapper.selectById("expert-001")).thenReturn(expert);
            when(workOrderService.getWorkOrderDetail(1L)).thenReturn(mockDetailVO);

            mockMvc.perform(post("/workorder/1/send-email")
                            .requestAttr("userId", "u-001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"toUserId\":\"expert-001\",\"subject\":\"自定义主题\",\"content\":\"自定义内容\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("邮件发送成功"));
        }

        @Test
        @DisplayName("收件人不存在返回400")
        void sendEmail_expertNotFound_returns400() throws Exception {
            mockCurrentUser();
            when(workOrderService.getWorkOrderDetail(1L)).thenReturn(mockDetailVO);
            when(sysUserMapper.selectById("not-exist")).thenReturn(null);

            mockMvc.perform(post("/workorder/1/send-email")
                            .requestAttr("userId", "u-001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"toUserId\":\"not-exist\",\"subject\":\"主题\",\"content\":\"内容\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("收件人邮箱不存在"));
        }

        @Test
        @DisplayName("SMTP 发送失败返回500")
        void sendEmail_sendFailure_returns500() throws Exception {
            mockCurrentUser();
            SysUser expert = new SysUser();
            expert.setId("expert-001");
            expert.setName("刘专家");
            expert.setEmail("expert@test.com");
            when(sysUserMapper.selectById("expert-001")).thenReturn(expert);
            when(workOrderService.getWorkOrderDetail(1L)).thenReturn(mockDetailVO);

            org.mockito.Mockito.doThrow(new RuntimeException("Connection refused"))
                    .when(emailService).sendEmail(anyString(), anyString(), anyString());

            mockMvc.perform(post("/workorder/1/send-email")
                            .requestAttr("userId", "u-001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"toUserId\":\"expert-001\",\"subject\":\"主题\",\"content\":\"内容\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.message").value("邮件发送失败: Connection refused"));
        }
    }
}
