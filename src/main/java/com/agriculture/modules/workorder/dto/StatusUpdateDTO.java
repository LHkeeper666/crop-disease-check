package com.agriculture.modules.workorder.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;

@Data
public class StatusUpdateDTO {

    @NotBlank(message = "状态不能为空")
    private String status;

    private String comment;

    private String expertComment;
}
