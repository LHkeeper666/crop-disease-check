package com.agriculture.modules.workorder.vo;

import lombok.Data;

@Data
public class CallbackResponseVO {

    private String workorderId;
    private String newStatus;
}
