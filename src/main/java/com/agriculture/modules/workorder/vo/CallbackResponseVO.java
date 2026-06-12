package com.agriculture.modules.workorder.vo;

import lombok.Data;

@Data
public class CallbackResponseVO {

    private Long workorderId;
    private String newStatus;
}
