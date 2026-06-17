package com.agriculture.modules.workorder.service;

import com.agriculture.common.mq.event.DetectionEvent;
import com.agriculture.modules.workorder.dto.CallbackDTO;
import com.agriculture.modules.workorder.dto.WorkOrderCreateDTO;
import com.agriculture.modules.workorder.dto.WorkOrderManualCreateDTO;
import com.agriculture.modules.workorder.entity.WorkOrder;
import com.agriculture.modules.workorder.vo.CallbackResponseVO;
import com.agriculture.modules.workorder.vo.EmailPreviewVO;
import com.agriculture.modules.workorder.vo.WorkOrderDetailVO;
import com.agriculture.modules.workorder.vo.WorkOrderVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

import java.time.LocalDateTime;

public interface WorkOrderService extends IService<WorkOrder> {

    IPage<WorkOrderVO> listWorkOrders(String status, String severity,
                                       LocalDateTime startDate, LocalDateTime endDate,
                                       int page, int size);

    IPage<WorkOrderVO> listWorkOrders(String status, String severity,
                                       LocalDateTime startDate, LocalDateTime endDate,
                                       int page, int size, String companyId);

    /** 带企业隔离 + 负责人过滤的分页查询（专家角色专用） */
    IPage<WorkOrderVO> listWorkOrders(String status, String severity,
                                       LocalDateTime startDate, LocalDateTime endDate,
                                       int page, int size, String companyId, String assignedTo);

    WorkOrderDetailVO getWorkOrderDetail(Long id);

    Long createWorkOrder(WorkOrderCreateDTO dto, String operatorId, String operatorName, String companyId);

    Long createManualWorkOrder(WorkOrderManualCreateDTO dto, String operatorId, String operatorName, String companyId);

    CallbackResponseVO handleCallback(CallbackDTO dto);

    void updateStatus(Long id, String status, String comment, String expertComment, String operatorId, String operatorName);

    void updateSeverity(Long id, String severity);

    void updateAssignee(Long id, String assignedTo);

    void deleteWorkOrder(Long id);

    EmailPreviewVO previewEmail(Long workOrderId);

    /** 从 MQ 检测事件创建工单（含去重：同企业+同网格+同病虫害+活跃工单已存在时只更新置信度） */
    void createFromDetectionEvent(DetectionEvent event);
}
