package com.agriculture.service;

import com.agriculture.modules.agriBrain.dto.PageContext;
import com.agriculture.modules.agriBrain.dto.VisibleData;
import com.agriculture.modules.agriBrain.service.impl.ContextBuilderImpl;
import com.agriculture.modules.camera.entity.Camera;
import com.agriculture.modules.camera.service.CameraService;
import com.agriculture.modules.environment.service.EnvironmentService;
import com.agriculture.modules.greenhouse.entity.Greenhouse;
import com.agriculture.modules.greenhouse.service.GreenhouseService;
import com.agriculture.modules.inference.entity.Inference;
import com.agriculture.modules.inference.service.InferenceService;
import com.agriculture.modules.pestDiseaseInfo.entity.DiseaseInfo;
import com.agriculture.modules.pestDiseaseInfo.entity.PestInfo;
import com.agriculture.modules.pestDiseaseInfo.service.DiseaseInfoService;
import com.agriculture.modules.pestDiseaseInfo.service.PestInfoService;
import com.agriculture.modules.report.entity.Report;
import com.agriculture.modules.report.service.ReportService;
import com.agriculture.modules.workorder.entity.WorkOrder;
import com.agriculture.modules.workorder.service.WorkOrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContextBuilderImplTest {

    @Mock
    private WorkOrderService workOrderService;

    @Mock
    private CameraService cameraService;

    @Mock
    private GreenhouseService greenhouseService;

    @Mock
    private ReportService reportService;

    @Mock
    private InferenceService inferenceService;

    @Mock
    private EnvironmentService environmentService;

    @Mock
    private PestInfoService pestInfoService;

    @Mock
    private DiseaseInfoService diseaseInfoService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ContextBuilderImpl contextBuilder;

    private static final String USER_ID = "user-001";
    private static final String COMPANY_ID = "company-001";

    @Nested
    @DisplayName("buildContext 方法")
    class BuildContext {

        @Test
        @DisplayName("null context 返回空字符串")
        void buildContext_nullContext_returnsEmpty() {
            String result = contextBuilder.buildContext(null, USER_ID, COMPANY_ID);
            assertEquals("", result);
        }

        @Test
        @DisplayName("只有 pageName 的空上下文")
        void buildContext_onlyPageName_returnsPageName() {
            PageContext ctx = new PageContext();
            ctx.setPage("/dashboard");
            ctx.setPageName("遥测总览");

            String result = contextBuilder.buildContext(ctx, USER_ID, COMPANY_ID);
            assertTrue(result.contains("当前页面: 遥测总览"));
        }

        @Test
        @DisplayName("未知页面只输出 pageName 和 visibleData")
        void buildContext_unknownPage_returnsPageNameAndVisibleData() {
            PageContext ctx = new PageContext();
            ctx.setPage("/unknown");
            ctx.setPageName("未知页面");
            ctx.setSelectedId("123");

            VisibleData vd = new VisibleData();
            vd.setStats(Map.of("total", 10));
            ctx.setVisibleData(vd);

            String result = contextBuilder.buildContext(ctx, USER_ID, COMPANY_ID);
            assertTrue(result.contains("当前页面: 未知页面"));
            assertTrue(result.contains("页面统计数据"));
            assertTrue(result.contains("\"total\":10"));
        }
    }

    @Nested
    @DisplayName("工单页面上下文")
    class WorkOrderContext {

        @Test
        @DisplayName("selectedId 存在且属于同一公司")
        void buildContext_workOrderExists_returnsDetail() {
            WorkOrder wo = new WorkOrder();
            wo.setId(1L);
            wo.setTitle("温室A区番茄病虫害防治");
            wo.setStatus("PENDING");
            wo.setSeverity("HIGH");
            wo.setCompanyId(COMPANY_ID);

            when(workOrderService.getById(1L)).thenReturn(wo);

            PageContext ctx = new PageContext();
            ctx.setPage("/workorders");
            ctx.setPageName("工单管理");
            ctx.setSelectedId("1");

            String result = contextBuilder.buildContext(ctx, USER_ID, COMPANY_ID);
            assertTrue(result.contains("用户当前选中的资源"));
            assertTrue(result.contains("温室A区番茄病虫害防治"));
            assertTrue(result.contains("PENDING"));
        }

        @Test
        @DisplayName("selectedId 存在但不属于同一公司")
        void buildContext_workOrderDifferentCompany_returnsNull() {
            WorkOrder wo = new WorkOrder();
            wo.setId(1L);
            wo.setTitle("其他公司工单");
            wo.setCompanyId("other-company");

            when(workOrderService.getById(1L)).thenReturn(wo);

            PageContext ctx = new PageContext();
            ctx.setPage("/workorders");
            ctx.setPageName("工单管理");
            ctx.setSelectedId("1");

            String result = contextBuilder.buildContext(ctx, USER_ID, COMPANY_ID);
            assertFalse(result.contains("用户当前选中的资源"));
        }

        @Test
        @DisplayName("selectedId 不存在")
        void buildContext_workOrderNotExists_returnsNoDetail() {
            when(workOrderService.getById(999L)).thenReturn(null);

            PageContext ctx = new PageContext();
            ctx.setPage("/workorders");
            ctx.setPageName("工单管理");
            ctx.setSelectedId("999");

            String result = contextBuilder.buildContext(ctx, USER_ID, COMPANY_ID);
            assertFalse(result.contains("用户当前选中的资源"));
            assertTrue(result.contains("当前页面: 工单管理"));
        }

        @Test
        @DisplayName("selectedId 格式无效")
        void buildContext_workOrderInvalidId_returnsNoDetail() {
            PageContext ctx = new PageContext();
            ctx.setPage("/workorders");
            ctx.setPageName("工单管理");
            ctx.setSelectedId("abc");

            String result = contextBuilder.buildContext(ctx, USER_ID, COMPANY_ID);
            assertFalse(result.contains("用户当前选中的资源"));
        }
    }

    @Nested
    @DisplayName("摄像头监控页面上下文")
    class MonitorContext {

        @Test
        @DisplayName("selectedId 存在且属于同一公司")
        void buildContext_cameraExists_returnsDetail() {
            Camera cam = new Camera();
            cam.setId("cam-001");
            cam.setName("温室A摄像头");
            cam.setStatus("ONLINE");
            cam.setCompanyId(COMPANY_ID);

            when(cameraService.getById("cam-001")).thenReturn(cam);

            PageContext ctx = new PageContext();
            ctx.setPage("/monitor");
            ctx.setPageName("摄像头监控");
            ctx.setSelectedId("cam-001");

            String result = contextBuilder.buildContext(ctx, USER_ID, COMPANY_ID);
            assertTrue(result.contains("用户当前选中的资源"));
            assertTrue(result.contains("温室A摄像头"));
        }

        @Test
        @DisplayName("selectedId 存在但不属于同一公司")
        void buildContext_cameraDifferentCompany_returnsNull() {
            Camera cam = new Camera();
            cam.setId("cam-001");
            cam.setName("其他公司摄像头");
            cam.setCompanyId("other-company");

            when(cameraService.getById("cam-001")).thenReturn(cam);

            PageContext ctx = new PageContext();
            ctx.setPage("/monitor");
            ctx.setPageName("摄像头监控");
            ctx.setSelectedId("cam-001");

            String result = contextBuilder.buildContext(ctx, USER_ID, COMPANY_ID);
            assertFalse(result.contains("用户当前选中的资源"));
        }
    }

    @Nested
    @DisplayName("遥测总览页面上下文")
    class DashboardContext {

        @Test
        @DisplayName("只有 visibleData，无 selectedId")
        void buildContext_dashboardVisibleData_returnsStats() {
            PageContext ctx = new PageContext();
            ctx.setPage("/dashboard");
            ctx.setPageName("遥测总览");

            VisibleData vd = new VisibleData();
            vd.setStats(Map.of(
                    "totalWorkOrders", 15,
                    "pendingWorkOrders", 5,
                    "criticalAlerts", 2
            ));
            ctx.setVisibleData(vd);

            String result = contextBuilder.buildContext(ctx, USER_ID, COMPANY_ID);
            assertTrue(result.contains("页面统计数据"));
            assertTrue(result.contains("\"totalWorkOrders\":15"));
            assertTrue(result.contains("\"criticalAlerts\":2"));
            assertFalse(result.contains("用户当前选中的资源"));
        }
    }

    @Nested
    @DisplayName("visibleData 格式化")
    class VisibleDataFormat {

        @Test
        @DisplayName("包含 list、stats、filters、extra")
        void buildContext_allVisibleDataFields_returnsFormatted() {
            PageContext ctx = new PageContext();
            ctx.setPage("/workorders");
            ctx.setPageName("工单管理");

            VisibleData vd = new VisibleData();
            vd.setList(List.of(
                    Map.of("id", 1, "title", "工单1", "status", "pending"),
                    Map.of("id", 2, "title", "工单2", "status", "processing")
            ));
            vd.setStats(Map.of("total", 10, "pending", 3));
            vd.setFilters(Map.of("status", "pending"));
            vd.setExtra(Map.of("topPriority", Map.of("id", 1, "severity", "critical")));

            ctx.setVisibleData(vd);

            String result = contextBuilder.buildContext(ctx, USER_ID, COMPANY_ID);
            assertTrue(result.contains("当前可见列表（前2条）"));
            assertTrue(result.contains("页面统计数据"));
            assertTrue(result.contains("当前筛选条件"));
            assertTrue(result.contains("其他信息"));
        }

        @Test
        @DisplayName("空 visibleData 不输出任何内容")
        void buildContext_emptyVisibleData_returnsNothing() {
            PageContext ctx = new PageContext();
            ctx.setPage("/workorders");
            ctx.setPageName("工单管理");

            VisibleData vd = new VisibleData();
            ctx.setVisibleData(vd);

            String result = contextBuilder.buildContext(ctx, USER_ID, COMPANY_ID);
            assertFalse(result.contains("页面统计数据"));
            assertFalse(result.contains("当前可见列表"));
        }
    }

    @Nested
    @DisplayName("selectedId + visibleData 合并输出")
    class CombinedContext {

        @Test
        @DisplayName("同时有 selectedId 和 visibleData")
        void buildContext_bothSelectedAndVisible_returnsCombined() {
            WorkOrder wo = new WorkOrder();
            wo.setId(1L);
            wo.setTitle("选中的工单");
            wo.setCompanyId(COMPANY_ID);

            when(workOrderService.getById(1L)).thenReturn(wo);

            PageContext ctx = new PageContext();
            ctx.setPage("/workorders");
            ctx.setPageName("工单管理");
            ctx.setSelectedId("1");

            VisibleData vd = new VisibleData();
            vd.setStats(Map.of("total", 10));
            vd.setList(List.of(Map.of("id", 1, "title", "工单1")));
            ctx.setVisibleData(vd);

            String result = contextBuilder.buildContext(ctx, USER_ID, COMPANY_ID);
            assertTrue(result.contains("用户当前选中的资源"));
            assertTrue(result.contains("选中的工单"));
            assertTrue(result.contains("页面统计数据"));
            assertTrue(result.contains("当前可见列表"));
        }
    }

    @Nested
    @DisplayName("其他页面上下文")
    class OtherPageContext {

        @Test
        @DisplayName("设备页面 selectedId")
        void buildContext_devicePage_returnsDetail() {
            Camera cam = new Camera();
            cam.setId("dev-001");
            cam.setName("设备1");
            cam.setCompanyId(COMPANY_ID);

            when(cameraService.getById("dev-001")).thenReturn(cam);

            PageContext ctx = new PageContext();
            ctx.setPage("/devices");
            ctx.setPageName("设备管理");
            ctx.setSelectedId("dev-001");

            String result = contextBuilder.buildContext(ctx, USER_ID, COMPANY_ID);
            assertTrue(result.contains("用户当前选中的资源"));
            assertTrue(result.contains("设备1"));
        }

        @Test
        @DisplayName("报告页面 selectedId")
        void buildContext_reportPage_returnsDetail() {
            Report report = new Report();
            report.setId("rpt-001");
            report.setDescription("日报-2026-06-16");

            when(reportService.getById("rpt-001")).thenReturn(report);

            PageContext ctx = new PageContext();
            ctx.setPage("/reports");
            ctx.setPageName("报告管理");
            ctx.setSelectedId("rpt-001");

            String result = contextBuilder.buildContext(ctx, USER_ID, COMPANY_ID);
            assertTrue(result.contains("用户当前选中的资源"));
            assertTrue(result.contains("日报-2026-06-16"));
        }

        @Test
        @DisplayName("知识手册页面 selectedId - 病害信息")
        void buildContext_handbookDisease_returnsDetail() {
            DiseaseInfo disease = new DiseaseInfo();
            disease.setId(1);
            disease.setNameCn("番茄晚疫病");

            when(diseaseInfoService.getById("1")).thenReturn(disease);

            PageContext ctx = new PageContext();
            ctx.setPage("/handbook");
            ctx.setPageName("知识手册");
            ctx.setSelectedId("1");

            String result = contextBuilder.buildContext(ctx, USER_ID, COMPANY_ID);
            assertTrue(result.contains("用户当前选中的资源"));
            assertTrue(result.contains("番茄晚疫病"));
        }

        @Test
        @DisplayName("知识手册页面 selectedId - 虫害信息")
        void buildContext_handbookPest_returnsDetail() {
            when(diseaseInfoService.getById("1")).thenReturn(null);

            PestInfo pest = new PestInfo();
            pest.setId(1);
            pest.setPestName("蚜虫");

            when(pestInfoService.getById("1")).thenReturn(pest);

            PageContext ctx = new PageContext();
            ctx.setPage("/handbook");
            ctx.setPageName("知识手册");
            ctx.setSelectedId("1");

            String result = contextBuilder.buildContext(ctx, USER_ID, COMPANY_ID);
            assertTrue(result.contains("用户当前选中的资源"));
            assertTrue(result.contains("蚜虫"));
        }
    }
}
