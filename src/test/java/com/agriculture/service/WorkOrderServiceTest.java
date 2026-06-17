package com.agriculture.service;

import com.agriculture.common.config.LlmProperties;
import com.agriculture.common.exception.BusinessException;
import com.agriculture.common.service.TemplateService;
import com.agriculture.common.websocket.WebSocketService;
import com.agriculture.modules.grid.entity.Grid;
import com.agriculture.modules.grid.mapper.GridMapper;
import com.agriculture.modules.workorder.entity.WorkOrder;
import com.agriculture.modules.workorder.entity.WorkOrderHistory;
import com.agriculture.modules.workorder.mapper.WorkOrderHistoryMapper;
import com.agriculture.modules.workorder.mapper.WorkOrderMapper;
import com.agriculture.modules.workorder.service.impl.WorkOrderServiceImpl;
import com.agriculture.modules.workorder.vo.EmailPreviewVO;
import com.agriculture.modules.workorder.vo.WorkOrderDetailVO;
import com.agriculture.modules.user.entity.SysUser;
import com.agriculture.modules.user.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkOrderService 单元测试")
class WorkOrderServiceTest {

    @Mock
    private WorkOrderMapper workOrderMapper;

    @Mock
    private WorkOrderHistoryMapper workOrderHistoryMapper;

    @Mock
    private SysUserMapper sysUserMapper;

    @Mock
    private WebSocketService webSocketService;

    @Mock
    private TemplateService templateService;

    @Mock
    private LlmProperties llmProperties;

    @Mock
    private RestClient llmRestClient;

    @Mock
    private GridMapper gridMapper;

    @InjectMocks
    private WorkOrderServiceImpl workOrderService;

    private WorkOrder mockWorkOrder;
    private SysUser mockExpert;

    @BeforeEach
    void setUp() throws Exception {
        // 注入 baseMapper（MyBatis-Plus ServiceImpl 需要）
        var baseMapperField = com.baomidou.mybatisplus.extension.service.impl.ServiceImpl.class.getDeclaredField("baseMapper");
        baseMapperField.setAccessible(true);
        baseMapperField.set(workOrderService, workOrderMapper);

        mockWorkOrder = new WorkOrder();
        mockWorkOrder.setId(1L);
        mockWorkOrder.setTitle("【HIGH】番茄晚疫病 工单");
        mockWorkOrder.setSeverity("HIGH");
        mockWorkOrder.setStatus("PENDING");
        mockWorkOrder.setType("DISEASE");
        mockWorkOrder.setGridLabel("A1");
        mockWorkOrder.setPestName("番茄晚疫病");
        mockWorkOrder.setConfidence(new BigDecimal("0.92"));
        mockWorkOrder.setAssignedTo("expert-001");
        mockWorkOrder.setCallbackToken("test-token-abc");
        mockWorkOrder.setTokenExpireAt(LocalDateTime.now().plusDays(7));
        mockWorkOrder.setTokenUsed((byte) 0);
        mockWorkOrder.setCreatedBy("u-001");
        mockWorkOrder.setCompanyId("company-001");
        mockWorkOrder.setCreatedAt(LocalDateTime.of(2026, 6, 15, 10, 0, 0));
        mockWorkOrder.setUpdatedAt(LocalDateTime.of(2026, 6, 15, 10, 0, 0));

        mockExpert = new SysUser();
        mockExpert.setId("expert-001");
        mockExpert.setName("刘专家");
        mockExpert.setEmail("expert@test.com");
    }

    // ==================== previewEmail ====================

    @Nested
    @DisplayName("previewEmail 方法")
    class PreviewEmail {

        @Test
        @DisplayName("正常返回邮件预览数据（AI 不可用时回退到模板）")
        void previewEmail_withAssignedExpert_returnsPreview() {
            when(workOrderMapper.selectById(1L)).thenReturn(mockWorkOrder);
            when(sysUserMapper.selectById("expert-001")).thenReturn(mockExpert);
            when(templateService.render(eq("email_prompt"), any(Map.class)))
                    .thenReturn("prompt");
            when(llmProperties.getModel()).thenReturn("deepseek-chat");
            // RestClient 为 final class，无法 mock，AI 调用会失败并回退到模板

            EmailPreviewVO result = workOrderService.previewEmail(1L);

            assertNotNull(result);
            assertEquals("expert-001", result.getToUserId());
            assertEquals("刘专家", result.getToName());
            assertEquals("expert@test.com", result.getToEmail());
            assertTrue(result.getSubject().contains("番茄晚疫病"));
            // 回退内容包含工单详情
            assertTrue(result.getContent().contains("番茄晚疫病"));
            assertTrue(result.getContent().contains("HIGH"));
            assertTrue(result.getContent().contains("A1"));
            assertTrue(result.getContent().contains("92%"));
        }

        @Test
        @DisplayName("工单不存在抛异常")
        void previewEmail_workOrderNotFound_throwsException() {
            when(workOrderMapper.selectById(999L)).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> workOrderService.previewEmail(999L));
            assertEquals(404, ex.getCode());
        }

        @Test
        @DisplayName("未指定负责人抛异常")
        void previewEmail_noAssignedTo_throwsException() {
            WorkOrder noAssignee = new WorkOrder();
            noAssignee.setId(2L);
            noAssignee.setTitle("测试工单");
            noAssignee.setAssignedTo(null);
            when(workOrderMapper.selectById(2L)).thenReturn(noAssignee);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> workOrderService.previewEmail(2L));
            assertTrue(ex.getMessage().contains("未指定负责人"));
        }

        @Test
        @DisplayName("专家无邮箱抛异常")
        void previewEmail_expertNoEmail_throwsException() {
            when(workOrderMapper.selectById(1L)).thenReturn(mockWorkOrder);
            SysUser noEmailExpert = new SysUser();
            noEmailExpert.setId("expert-001");
            noEmailExpert.setName("刘专家");
            noEmailExpert.setEmail(null);
            when(sysUserMapper.selectById("expert-001")).thenReturn(noEmailExpert);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> workOrderService.previewEmail(1L));
            assertTrue(ex.getMessage().contains("未配置邮箱"));
        }

        @Test
        @DisplayName("AI 调用失败时回退到结构化模板")
        void previewEmail_aiCallFallsBackToTemplate() {
            when(workOrderMapper.selectById(1L)).thenReturn(mockWorkOrder);
            when(sysUserMapper.selectById("expert-001")).thenReturn(mockExpert);
            when(templateService.render(eq("email_prompt"), any(Map.class)))
                    .thenReturn("prompt");
            when(llmProperties.getModel()).thenReturn("deepseek-chat");

            // 模拟 AI 调用抛异常
            when(llmRestClient.post()).thenThrow(new RuntimeException("Connection refused"));

            EmailPreviewVO result = workOrderService.previewEmail(1L);

            assertNotNull(result);
            assertEquals("expert-001", result.getToUserId());
            // 回退内容应包含工单信息
            assertTrue(result.getContent().contains("番茄晚疫病"));
            assertTrue(result.getContent().contains("HIGH"));
            assertTrue(result.getContent().contains("A1"));
        }
    }

    // ==================== getWorkOrderDetail ====================

    @Nested
    @DisplayName("getWorkOrderDetail 方法")
    class GetWorkOrderDetail {

        @Test
        @DisplayName("返回的 VO 包含负责人邮箱")
        void getDetail_populatesAssignedToEmail() {
            when(workOrderMapper.selectById(1L)).thenReturn(mockWorkOrder);
            when(sysUserMapper.selectById("expert-001")).thenReturn(mockExpert);
            when(workOrderHistoryMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of());

            WorkOrderDetailVO result = workOrderService.getWorkOrderDetail(1L);

            assertNotNull(result);
            assertEquals("expert@test.com", result.getAssignedToEmail());
        }

        @Test
        @DisplayName("无负责人时邮箱字段为 null")
        void getDetail_noAssignedTo_emailIsNull() {
            WorkOrder noAssignee = new WorkOrder();
            noAssignee.setId(2L);
            noAssignee.setTitle("测试工单");
            noAssignee.setAssignedTo(null);
            noAssignee.setCreatedAt(LocalDateTime.now());
            noAssignee.setUpdatedAt(LocalDateTime.now());
            when(workOrderMapper.selectById(2L)).thenReturn(noAssignee);
            when(workOrderHistoryMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of());

            WorkOrderDetailVO result = workOrderService.getWorkOrderDetail(2L);

            assertNotNull(result);
            assertNull(result.getAssignedToEmail());
        }
    }

    // ==================== reviewWorkOrders (AI 审核) ====================

    @Nested
    @DisplayName("reviewWorkOrders 方法 (AI 审核)")
    class ReviewWorkOrders {

        private WorkOrder createAiReviewWorkOrder(Long id, String gridLabel, String pestName, double confidence) {
            WorkOrder wo = new WorkOrder();
            wo.setId(id);
            wo.setTitle("【HIGH】Grid-" + gridLabel + " " + pestName + " 自动检测");
            wo.setSeverity("HIGH");
            wo.setStatus(WorkOrder.STATUS_AI_REVIEW);
            wo.setType("disease");
            wo.setGridLabel(gridLabel);
            wo.setPestName(pestName);
            wo.setConfidence(BigDecimal.valueOf(confidence));
            wo.setCompanyId("company-001");
            wo.setCreatedAt(LocalDateTime.now());
            wo.setUpdatedAt(LocalDateTime.now());
            return wo;
        }

        @Test
        @DisplayName("LLM 异常时全部工单默认提升为 PENDING")
        void reviewWorkOrders_llmException_promotesAllToPending() {
            WorkOrder wo1 = createAiReviewWorkOrder(10L, "A1", "番茄晚疫病", 0.85);
            WorkOrder wo2 = createAiReviewWorkOrder(11L, "B2", "稻飞虱", 0.72);
            List<WorkOrder> workOrders = new ArrayList<>(List.of(wo1, wo2));

            // Grid 查询返回作物类型
            Grid gridA1 = new Grid();
            gridA1.setLabel("A1");
            gridA1.setCropType("番茄");
            when(gridMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(gridA1);

            when(templateService.render(eq("ai_review_prompt"), any(Map.class)))
                    .thenReturn("prompt");
            when(llmProperties.getModel()).thenReturn("deepseek-chat");
            // RestClient.post() 会抛异常（final class 无法 mock），触发兜底逻辑

            workOrderService.reviewWorkOrders(workOrders);

            // 两条工单都应被提升为 PENDING
            verify(workOrderMapper, times(2)).updateById(any(WorkOrder.class));
            verify(workOrderHistoryMapper, times(2)).insert(any(WorkOrderHistory.class));
            assertEquals("PENDING", wo1.getStatus());
            assertEquals("PENDING", wo2.getStatus());
        }

        @Test
        @DisplayName("空列表不执行任何操作")
        void reviewWorkOrders_emptyList_doesNothing() {
            workOrderService.reviewWorkOrders(new ArrayList<>());

            verify(workOrderMapper, never()).updateById(any(WorkOrder.class));
            verify(workOrderMapper, never()).deleteById(any(Long.class));
        }

        @Test
        @DisplayName("null 列表不执行任何操作")
        void reviewWorkOrders_nullList_doesNothing() {
            workOrderService.reviewWorkOrders(null);

            verify(workOrderMapper, never()).updateById(any(WorkOrder.class));
            verify(workOrderMapper, never()).deleteById(any(Long.class));
        }
    }
}
