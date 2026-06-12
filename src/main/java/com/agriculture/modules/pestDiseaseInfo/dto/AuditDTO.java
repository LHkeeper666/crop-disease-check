package com.agriculture.modules.pestDiseaseInfo.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;

/**
 * 审核上报请求DTO
 */
@Data
public class AuditDTO {

    @NotBlank(message = "操作类型不能为空")
    private String action;

    private String comment;
}
