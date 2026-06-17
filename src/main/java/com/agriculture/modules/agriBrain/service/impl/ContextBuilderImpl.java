package com.agriculture.modules.agriBrain.service.impl;

import com.agriculture.modules.agriBrain.dto.PageContext;
import com.agriculture.modules.agriBrain.dto.VisibleData;
import com.agriculture.modules.agriBrain.service.ContextBuilder;
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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ContextBuilderImpl implements ContextBuilder {

    @Resource
    private WorkOrderService workOrderService;

    @Resource
    private CameraService cameraService;

    @Resource
    private GreenhouseService greenhouseService;

    @Resource
    private ReportService reportService;

    @Resource
    private InferenceService inferenceService;

    @Resource
    private EnvironmentService environmentService;

    @Resource
    private PestInfoService pestInfoService;

    @Resource
    private DiseaseInfoService diseaseInfoService;

    @Resource
    private ObjectMapper objectMapper;

    @Override
    public String buildContext(PageContext context, String userId, String companyId) {
        if (context == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("当前页面: ").append(nullToEmpty(context.getPageName())).append("\n\n");

        // 1. 处理 selectedId（查数据库获取完整数据）
        if (context.getSelectedId() != null && !context.getSelectedId().isEmpty()) {
            String detail = querySelectedDetail(context.getPage(), context.getSelectedId(), companyId);
            if (detail != null) {
                sb.append("## 用户当前选中的资源\n").append(detail).append("\n\n");
            }
        }

        // 2. 处理 visibleData（直接格式化，不需要查询）
        if (context.getVisibleData() != null) {
            String visibleText = formatVisibleData(context.getVisibleData());
            if (!visibleText.isEmpty()) {
                sb.append(visibleText);
            }
        }

        String result = sb.toString().trim();
        return result.isEmpty() ? "" : result;
    }

    private String querySelectedDetail(String page, String selectedId, String companyId) {
        if (page == null) {
            return null;
        }

        try {
            switch (page) {
                case "/workorders":
                    return queryWorkOrder(selectedId, companyId);
                case "/monitor":
                    return queryCamera(selectedId, companyId);
                case "/devices":
                    return queryCamera(selectedId, companyId);
                case "/reports":
                    return queryReport(selectedId);
                case "/detection":
                    return queryInference(selectedId, companyId);
                case "/environment":
                    return queryEnvironment(selectedId, companyId);
                case "/handbook":
                    return queryHandbook(selectedId);
                case "/dashboard":
                    return null; // 总览页无 selectedId
                default:
                    return null;
            }
        } catch (Exception e) {
            log.warn("查询页面上下文失败: page={}, selectedId={}", page, selectedId, e);
            return null;
        }
    }

    private String queryWorkOrder(String selectedId, String companyId) {
        try {
            Long id = Long.parseLong(selectedId);
            WorkOrder wo = workOrderService.getById(id);
            if (wo == null || !wo.getCompanyId().equals(companyId)) {
                return null;
            }
            return toJson(wo);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String queryCamera(String selectedId, String companyId) {
        Camera cam = cameraService.getById(selectedId);
        if (cam == null || !cam.getCompanyId().equals(companyId)) {
            return null;
        }
        return toJson(cam);
    }

    private String queryReport(String selectedId) {
        Report report = reportService.getById(selectedId);
        return report != null ? toJson(report) : null;
    }

    private String queryInference(String selectedId, String companyId) {
        Inference inference = inferenceService.getById(selectedId);
        if (inference == null || !inference.getCompanyId().equals(companyId)) {
            return null;
        }
        return toJson(inference);
    }

    private String queryEnvironment(String selectedId, String companyId) {
        // 环境数据按 greenhouseId 查询，selectedId 可能是 greenhouseId
        Greenhouse gh = greenhouseService.getById(selectedId);
        if (gh == null || !gh.getCompanyId().equals(companyId)) {
            return null;
        }
        return toJson(gh);
    }

    private String queryHandbook(String selectedId) {
        // 知识手册可能是病害或虫害信息，先尝试病害，再尝试虫害
        DiseaseInfo disease = diseaseInfoService.getById(selectedId);
        if (disease != null) {
            return toJson(disease);
        }
        PestInfo pest = pestInfoService.getById(selectedId);
        return pest != null ? toJson(pest) : null;
    }

    private String formatVisibleData(VisibleData vd) {
        StringBuilder sb = new StringBuilder();

        if (vd.getStats() != null && !vd.getStats().isEmpty()) {
            sb.append("## 页面统计数据\n");
            sb.append(toJson(vd.getStats())).append("\n\n");
        }

        if (vd.getList() != null && !vd.getList().isEmpty()) {
            sb.append("## 当前可见列表（前").append(vd.getList().size()).append("条）\n");
            sb.append(toJson(vd.getList())).append("\n\n");
        }

        if (vd.getFilters() != null && !vd.getFilters().isEmpty()) {
            sb.append("## 当前筛选条件\n");
            sb.append(toJson(vd.getFilters())).append("\n\n");
        }

        if (vd.getExtra() != null && !vd.getExtra().isEmpty()) {
            sb.append("## 其他信息\n");
            sb.append(toJson(vd.getExtra())).append("\n\n");
        }

        return sb.toString();
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("JSON 序列化失败", e);
            return "{}";
        }
    }

    private String nullToEmpty(String s) {
        return s != null ? s : "";
    }
}
