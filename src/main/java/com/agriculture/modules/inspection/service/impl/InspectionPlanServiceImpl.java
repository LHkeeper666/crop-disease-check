package com.agriculture.modules.inspection.service.impl;

import com.agriculture.modules.inspection.entity.InspectionPlan;
import com.agriculture.modules.inspection.mapper.InspectionPlanMapper;
import com.agriculture.modules.inspection.service.InspectionPlanService;
import com.agriculture.common.websocket.WebSocketService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * <p>
 * 巡检计划表 服务实现类
 * </p>
 *
 * @author agriculture-team
 * @since 2026-06-09
 */
@Service
public class InspectionPlanServiceImpl extends ServiceImpl<InspectionPlanMapper, InspectionPlan> implements InspectionPlanService {

    @Resource
    private WebSocketService webSocketService;

    /**
     * 推送巡检状态到 WebSocket
     *
     * @param planId    计划ID
     * @param status    状态（RUNNING/COMPLETED/FAILED）
     * @param total     总数
     * @param completed 已完成数
     * @param current   当前处理项
     */
    public void sendInspectionStatus(String planId, String status, int total, int completed, String current) {
        try {
            Map<String, Object> wsData = new HashMap<>();
            wsData.put("planId", planId);
            wsData.put("status", status);

            Map<String, Object> progress = new HashMap<>();
            progress.put("total", total);
            progress.put("completed", completed);
            progress.put("current", current);
            wsData.put("progress", progress);

            webSocketService.sendInspectionStatus(wsData);
        } catch (Exception e) {
            // 推送失败不影响主流程
        }
    }
}
