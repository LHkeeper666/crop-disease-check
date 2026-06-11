package com.agriculture.modules.workorder.service;

import com.agriculture.modules.workorder.dto.CallbackDTO;
import com.agriculture.modules.workorder.dto.WorkOrderCreateDTO;
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

    WorkOrderDetailVO getWorkOrderDetail(String id);

    String createWorkOrder(WorkOrderCreateDTO dto, String operatorId, String operatorName);

    CallbackResponseVO handleCallback(CallbackDTO dto);
}
