package com.agriculture.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

/**
 * 制定/修改防治方案请求DTO
 */
@Data
public class PreventionPlanDTO {

    @NotBlank(message = "方案内容不能为空")
    private String content;

    private LocalDate suggestTime;
}
