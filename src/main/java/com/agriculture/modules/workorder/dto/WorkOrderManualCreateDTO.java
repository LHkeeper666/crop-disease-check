package com.agriculture.modules.workorder.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;

/**
 * 手动创建工单（不依赖推理记录）
 */
@Data
public class WorkOrderManualCreateDTO {

    @NotBlank(message = "工单标题不能为空")
    private String title;

    @NotBlank(message = "严重程度不能为空")
    private String severity;

    private String type;

    private String gridLabel;

    private String pestName;

    private Double confidence;

    private String assignedTo;
}
