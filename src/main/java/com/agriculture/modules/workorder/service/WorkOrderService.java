package com.agriculture.modules.workorder.service;

import com.agriculture.modules.workorder.dto.CallbackDTO;
import com.agriculture.modules.workorder.dto.WorkOrderCreateDTO;
import com.agriculture.modules.workorder.dto.WorkOrderManualCreateDTO;
import com.agriculture.modules.workorder.entity.WorkOrder;
import com.agriculture.modules.workorder.vo.CallbackResponseVO;
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

    WorkOrderDetailVO getWorkOrderDetail(Long id);

    Long createWorkOrder(WorkOrderCreateDTO dto, String operatorId, String operatorName, String companyId);

    Long createManualWorkOrder(WorkOrderManualCreateDTO dto, String operatorId, String operatorName, String companyId);

    CallbackResponseVO handleCallback(CallbackDTO dto);

    void updateStatus(Long id, String status, String comment, String operatorId, String operatorName);

    void updateSeverity(Long id, String severity);

    void deleteWorkOrder(Long id);
}
