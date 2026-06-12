package com.agriculture.modules.workorder.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;

@Data
public class CallbackDTO {

    @NotBlank(message = "Token不能为空")
    private String token;

    @NotBlank(message = "操作类型不能为空")
    private String action;

    private String comment;
}
