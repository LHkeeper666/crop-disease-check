package com.agriculture.modules.workorder.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;

@Data
public class WorkOrderCreateDTO {

    @NotBlank(message = "关联识别ID不能为空")
    private String inferenceId;

    @NotBlank(message = "严重程度不能为空")
    private String severity;

    private String assignedTo;
}
