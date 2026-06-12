package com.agriculture.modules.workorder.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;

@Data
public class SeverityUpdateDTO {

    @NotBlank(message = "严重程度不能为空")
    private String severity;
}
