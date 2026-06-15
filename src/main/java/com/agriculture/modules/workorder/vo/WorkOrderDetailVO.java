package com.agriculture.modules.workorder.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class WorkOrderDetailVO extends WorkOrderVO {

    private String inferenceId;
    private String expertComment;
    private String assignedToEmail;
    private List<StatusHistoryVO> statusHistory;
}
