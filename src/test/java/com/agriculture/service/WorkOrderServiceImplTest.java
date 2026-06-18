package com.agriculture.service;

import com.agriculture.common.exception.BusinessException;
import com.agriculture.common.websocket.WebSocketService;
import com.agriculture.modules.inference.entity.Inference;
import com.agriculture.modules.inference.mapper.InferenceMapper;
import com.agriculture.modules.user.entity.SysUser;
import com.agriculture.modules.user.mapper.SysUserMapper;
import com.agriculture.modules.workorder.dto.CallbackDTO;
import com.agriculture.modules.workorder.dto.WorkOrderCreateDTO;
import com.agriculture.modules.workorder.dto.WorkOrderManualCreateDTO;
import com.agriculture.modules.workorder.entity.WorkOrder;
import com.agriculture.modules.workorder.entity.WorkOrderHistory;
import com.agriculture.modules.workorder.mapper.WorkOrderHistoryMapper;
import com.agriculture.modules.workorder.mapper.WorkOrderMapper;
import com.agriculture.modules.workorder.service.impl.WorkOrderServiceImpl;
import com.agriculture.modules.workorder.vo.CallbackResponseVO;
import com.agriculture.modules.workorder.vo.WorkOrderDetailVO;
import com.agriculture.modules.workorder.vo.WorkOrderVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("WorkOrderServiceImpl 单元测试")
class WorkOrderServiceImplTest {

    @Mock private WorkOrderMapper workOrderMapper;
    @Mock private WorkOrderHistoryMapper historyMapper;
    @Mock private InferenceMapper inferenceMapper;
    @Mock private SysUserMapper sysUserMapper;
    @Mock private WebSocketService webSocketService;

    @InjectMocks
    private WorkOrderServiceImpl service;

    private WorkOrder sampleOrder;

    @BeforeEach
    void setUp() throws Exception {
        // WorkOrderServiceImpl extends ServiceImpl<WorkOrderMapper>，其 baseMapper 字段
        // 不会被 @InjectMocks 自动注入，需要通过反射手动设置
        Field baseMapperField = com.baomidou.mybatisplus.extension.service.impl.ServiceImpl.class
                .getDeclaredField("baseMapper");
        baseMapperField.setAccessible(true);
        baseMapperField.set(service, workOrderMapper);

        sampleOrder = new WorkOrder();
        sampleOrder.setId(1L);
        sampleOrder.setTitle("【HIGH】番茄晚疫病 工单");
        sampleOrder.setSeverity("HIGH");
        sampleOrder.setStatus("PENDING");
        sampleOrder.setType("disease");
        sampleOrder.setGridLabel("A1");
        sampleOrder.setPestName("番茄晚疫病");
        sampleOrder.setConfidence(new BigDecimal("0.92"));
        sampleOrder.setInferenceId("inf-001");
        sampleOrder.setAssignedTo("u-002");
        sampleOrder.setCreatedBy("u-001");
        sampleOrder.setCompanyId("company-001");
        sampleOrder.setCallbackToken("token123");
        sampleOrder.setTokenExpireAt(LocalDateTime.now().plusDays(7));
        sampleOrder.setTokenUsed((byte) 0);
        sampleOrder.setCreatedAt(LocalDateTime.now());
        sampleOrder.setUpdatedAt(LocalDateTime.now());
    }

    // ==================== listWorkOrders ====================

    @Nested
    @DisplayName("listWorkOrders - 工单列表")
    class ListWorkOrders {

        @Test
        @DisplayName("无条件查询返回分页数据")
        void listWorkOrders_noFilters_returnsPage() {
            Page<WorkOrder> page = new Page<>(1, 20, 1);
            page.setRecords(List.of(sampleOrder));
            when(workOrderMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

            SysUser user = new SysUser();
            user.setId("u-002");
            user.setName("李专家");
            when(sysUserMapper.selectById("u-002")).thenReturn(user);

            IPage<WorkOrderVO> result = service.listWorkOrders(null, null, null, null, 1, 20);

            assertEquals(1, result.getTotal());
            assertEquals(1L, result.getRecords().get(0).getId());
            assertEquals("李专家", result.getRecords().get(0).getAssignedToName());
        }

        @Test
        @DisplayName("带企业隔离查询")
        void listWorkOrders_withCompanyId_filtersByCompany() {
            Page<WorkOrder> page = new Page<>(1, 20, 0);
            page.setRecords(List.of());
            when(workOrderMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

            IPage<WorkOrderVO> result = service.listWorkOrders(null, null, null, null, 1, 20, "company-001");

            assertEquals(0, result.getTotal());
        }
    }

    // ==================== getWorkOrderDetail ====================

    @Nested
    @DisplayName("getWorkOrderDetail - 工单详情")
    class GetWorkOrderDetail {

        @Test
        @DisplayName("查询存在的工单返回详情")
        void getDetail_existing_returnsDetail() {
            when(workOrderMapper.selectById(1L)).thenReturn(sampleOrder);
            when(sysUserMapper.selectById("u-002")).thenReturn(createUser("u-002", "李专家"));
            when(historyMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

            WorkOrderDetailVO detail = service.getWorkOrderDetail(1L);

            assertEquals(1L, detail.getId());
            assertEquals("inf-001", detail.getInferenceId());
            assertNotNull(detail.getStatusHistory());
        }

        @Test
        @DisplayName("查询不存在的工单抛异常")
        void getDetail_notFound_throwsException() {
            when(workOrderMapper.selectById(999L)).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.getWorkOrderDetail(999L));
            assertEquals(404, ex.getCode());
        }
    }

    // ==================== createWorkOrder ====================

    @Nested
    @DisplayName("createWorkOrder - 基于推理记录创建")
    class CreateWorkOrder {

        @Test
        @DisplayName("创建成功返回工单ID")
        void create_validInference_returnsId() {
            Inference inference = new Inference();
            inference.setId("inf-001");
            inference.setDetections("[{\"name_cn\":\"番茄晚疫病\",\"pipeline\":\"disease\",\"confidence\":0.92}]");
            when(inferenceMapper.selectById("inf-001")).thenReturn(inference);
            // 模拟 MyBatis-Plus insert 后自动回填 ID
            doAnswer(invocation -> {
                WorkOrder wo = invocation.getArgument(0);
                wo.setId(100L);
                return 1;
            }).when(workOrderMapper).insert(any(WorkOrder.class));
            when(historyMapper.insert(any(WorkOrderHistory.class))).thenReturn(1);

            WorkOrderCreateDTO dto = new WorkOrderCreateDTO();
            dto.setInferenceId("inf-001");
            dto.setSeverity("HIGH");

            Long id = service.createWorkOrder(dto, "u-001", "管理员", "company-001");

            assertNotNull(id);
            verify(workOrderMapper).insert(any(WorkOrder.class));
            verify(historyMapper).insert(any(WorkOrderHistory.class));
            verify(webSocketService).sendWorkorderChange(anyMap());
        }

        @Test
        @DisplayName("推理记录不存在时抛异常")
        void create_invalidInference_throwsException() {
            when(inferenceMapper.selectById("not-exist")).thenReturn(null);

            WorkOrderCreateDTO dto = new WorkOrderCreateDTO();
            dto.setInferenceId("not-exist");
            dto.setSeverity("HIGH");

            assertThrows(BusinessException.class,
                    () -> service.createWorkOrder(dto, "u-001", "管理员", "company-001"));
        }
    }

    // ==================== createManualWorkOrder ====================

    @Nested
    @DisplayName("createManualWorkOrder - 手动创建")
    class CreateManualWorkOrder {

        @Test
        @DisplayName("手动创建成功")
        void createManual_valid_returnsId() {
            // 模拟 MyBatis-Plus insert 后自动回填 ID
            doAnswer(invocation -> {
                WorkOrder wo = invocation.getArgument(0);
                wo.setId(200L);
                return 1;
            }).when(workOrderMapper).insert(any(WorkOrder.class));
            when(historyMapper.insert(any(WorkOrderHistory.class))).thenReturn(1);

            WorkOrderManualCreateDTO dto = new WorkOrderManualCreateDTO();
            dto.setTitle("【紧急】Grid-B3 红蜘蛛 工单");
            dto.setSeverity("CRITICAL");
            dto.setType("pest");
            dto.setGridLabel("B3");
            dto.setPestName("红蜘蛛");
            dto.setConfidence(0.95);

            Long id = service.createManualWorkOrder(dto, "u-001", "管理员", "company-001");

            assertNotNull(id);
            ArgumentCaptor<WorkOrder> captor = ArgumentCaptor.forClass(WorkOrder.class);
            verify(workOrderMapper).insert(captor.capture());
            assertEquals("CRITICAL", captor.getValue().getSeverity());
            assertEquals("B3", captor.getValue().getGridLabel());
            assertEquals("company-001", captor.getValue().getCompanyId());
        }
    }

    // ==================== updateStatus ====================

    @Nested
    @DisplayName("updateStatus - 状态更新")
    class UpdateStatus {

        @Test
        @DisplayName("PENDING → PROCESSING 合法流转")
        void updateStatus_pendingToProcessing_success() {
            sampleOrder.setStatus("PENDING");
            when(workOrderMapper.selectById(1L)).thenReturn(sampleOrder);
            when(workOrderMapper.updateById(any(WorkOrder.class))).thenReturn(1);
            when(historyMapper.insert(any(WorkOrderHistory.class))).thenReturn(1);

            service.updateStatus(1L, "PROCESSING", null, null, "u-001", "管理员");

            assertEquals("PROCESSING", sampleOrder.getStatus());
            verify(workOrderMapper).updateById(sampleOrder);
        }

        @Test
        @DisplayName("PROCESSING → DONE 合法流转")
        void updateStatus_processingToDone_success() {
            sampleOrder.setStatus("PROCESSING");
            when(workOrderMapper.selectById(1L)).thenReturn(sampleOrder);
            when(workOrderMapper.updateById(any(WorkOrder.class))).thenReturn(1);
            when(historyMapper.insert(any(WorkOrderHistory.class))).thenReturn(1);

            service.updateStatus(1L, "DONE", "已处理", null, "u-001", "管理员");

            assertEquals("DONE", sampleOrder.getStatus());
        }

        @Test
        @DisplayName("PENDING → IGNORED 合法流转")
        void updateStatus_pendingToIgnored_success() {
            sampleOrder.setStatus("PENDING");
            when(workOrderMapper.selectById(1L)).thenReturn(sampleOrder);
            when(workOrderMapper.updateById(any(WorkOrder.class))).thenReturn(1);
            when(historyMapper.insert(any(WorkOrderHistory.class))).thenReturn(1);

            service.updateStatus(1L, "IGNORED", "误报", null, "u-001", "管理员");

            assertEquals("IGNORED", sampleOrder.getStatus());
        }

        @Test
        @DisplayName("IGNORED → PENDING 恢复待处理")
        void updateStatus_ignoredToPending_success() {
            sampleOrder.setStatus("IGNORED");
            when(workOrderMapper.selectById(1L)).thenReturn(sampleOrder);
            when(workOrderMapper.updateById(any(WorkOrder.class))).thenReturn(1);
            when(historyMapper.insert(any(WorkOrderHistory.class))).thenReturn(1);

            service.updateStatus(1L, "PENDING", null, null, "u-001", "管理员");

            assertEquals("PENDING", sampleOrder.getStatus());
        }

        @Test
        @DisplayName("PENDING → DONE 非法流转抛异常")
        void updateStatus_pendingToDone_throwsException() {
            sampleOrder.setStatus("PENDING");
            when(workOrderMapper.selectById(1L)).thenReturn(sampleOrder);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.updateStatus(1L, "DONE", null, null, "u-001", "管理员"));
            assertTrue(ex.getMessage().contains("非法的状态变更"));
        }

        @Test
        @DisplayName("DONE → PENDING 非法流转抛异常")
        void updateStatus_doneToPending_throwsException() {
            sampleOrder.setStatus("DONE");
            when(workOrderMapper.selectById(1L)).thenReturn(sampleOrder);

            assertThrows(BusinessException.class,
                    () -> service.updateStatus(1L, "PENDING", null, null, "u-001", "管理员"));
        }

        @Test
        @DisplayName("工单不存在抛异常")
        void updateStatus_notFound_throwsException() {
            when(workOrderMapper.selectById(999L)).thenReturn(null);

            assertThrows(BusinessException.class,
                    () -> service.updateStatus(999L, "PROCESSING", null, null, "u-001", "管理员"));
        }
    }

    // ==================== updateSeverity ====================

    @Nested
    @DisplayName("updateSeverity - 严重程度更新")
    class UpdateSeverity {

        @Test
        @DisplayName("PENDING状态可升级")
        void updateSeverity_pending_canEscalate() {
            sampleOrder.setStatus("PENDING");
            sampleOrder.setSeverity("HIGH");
            when(workOrderMapper.selectById(1L)).thenReturn(sampleOrder);
            when(workOrderMapper.updateById(any(WorkOrder.class))).thenReturn(1);

            service.updateSeverity(1L, "CRITICAL");

            assertEquals("CRITICAL", sampleOrder.getSeverity());
        }

        @Test
        @DisplayName("DONE状态不能修改严重程度")
        void updateSeverity_done_throwsException() {
            sampleOrder.setStatus("DONE");
            when(workOrderMapper.selectById(1L)).thenReturn(sampleOrder);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.updateSeverity(1L, "CRITICAL"));
            assertTrue(ex.getMessage().contains("已完成或已忽略"));
        }

        @Test
        @DisplayName("IGNORED状态不能修改严重程度")
        void updateSeverity_ignored_throwsException() {
            sampleOrder.setStatus("IGNORED");
            when(workOrderMapper.selectById(1L)).thenReturn(sampleOrder);

            assertThrows(BusinessException.class,
                    () -> service.updateSeverity(1L, "LOW"));
        }
    }

    // ==================== deleteWorkOrder ====================

    @Nested
    @DisplayName("deleteWorkOrder - 删除工单")
    class DeleteWorkOrder {

        @Test
        @DisplayName("删除存在的工单成功")
        void delete_existing_success() {
            when(workOrderMapper.selectById(1L)).thenReturn(sampleOrder);
            when(historyMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(1);
            when(workOrderMapper.deleteById(1L)).thenReturn(1);

            service.deleteWorkOrder(1L);

            verify(historyMapper).delete(any(LambdaQueryWrapper.class));
            verify(workOrderMapper).deleteById(1L);
        }

        @Test
        @DisplayName("删除不存在的工单抛异常")
        void delete_notFound_throwsException() {
            when(workOrderMapper.selectById(999L)).thenReturn(null);

            assertThrows(BusinessException.class,
                    () -> service.deleteWorkOrder(999L));
        }
    }

    // ==================== handleCallback ====================

    @Nested
    @DisplayName("handleCallback - Token回调")
    class HandleCallback {

        @Test
        @DisplayName("专家确认 → DONE")
        void callback_confirm_success() {
            when(workOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(sampleOrder);
            when(workOrderMapper.updateById(any(WorkOrder.class))).thenReturn(1);
            when(historyMapper.insert(any(WorkOrderHistory.class))).thenReturn(1);

            CallbackDTO dto = new CallbackDTO();
            dto.setToken("token123");
            dto.setAction("CONFIRM");
            dto.setComment("已确认");

            CallbackResponseVO result = service.handleCallback(dto);

            assertEquals("DONE", result.getNewStatus());
            assertEquals((byte) 1, sampleOrder.getTokenUsed());
        }

        @Test
        @DisplayName("专家忽略 → IGNORED")
        void callback_ignore_success() {
            when(workOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(sampleOrder);
            when(workOrderMapper.updateById(any(WorkOrder.class))).thenReturn(1);
            when(historyMapper.insert(any(WorkOrderHistory.class))).thenReturn(1);

            CallbackDTO dto = new CallbackDTO();
            dto.setToken("token123");
            dto.setAction("IGNORE");

            CallbackResponseVO result = service.handleCallback(dto);

            assertEquals("IGNORED", result.getNewStatus());
        }

        @Test
        @DisplayName("Token无效抛异常")
        void callback_invalidToken_throwsException() {
            when(workOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            CallbackDTO dto = new CallbackDTO();
            dto.setToken("invalid");
            dto.setAction("CONFIRM");

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.handleCallback(dto));
            assertEquals(40060, ex.getCode());
        }

        @Test
        @DisplayName("Token已过期抛异常")
        void callback_expiredToken_throwsException() {
            sampleOrder.setTokenExpireAt(LocalDateTime.now().minusDays(1));
            when(workOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(sampleOrder);

            CallbackDTO dto = new CallbackDTO();
            dto.setToken("token123");
            dto.setAction("CONFIRM");

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.handleCallback(dto));
            assertEquals(40061, ex.getCode());
        }

        @Test
        @DisplayName("Token已使用抛异常")
        void callback_usedToken_throwsException() {
            sampleOrder.setTokenUsed((byte) 1);
            when(workOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(sampleOrder);

            CallbackDTO dto = new CallbackDTO();
            dto.setToken("token123");
            dto.setAction("CONFIRM");

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.handleCallback(dto));
            assertEquals(40062, ex.getCode());
        }
    }

    private SysUser createUser(String id, String name) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setName(name);
        return user;
    }
}
