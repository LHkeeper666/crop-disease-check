package com.agriculture.service;

import com.agriculture.dto.CallbackDTO;
import com.agriculture.dto.WorkOrderCreateDTO;
import com.agriculture.entity.WorkOrder;
import com.agriculture.vo.CallbackResponseVO;
import com.agriculture.vo.WorkOrderDetailVO;
import com.agriculture.vo.WorkOrderVO;
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
