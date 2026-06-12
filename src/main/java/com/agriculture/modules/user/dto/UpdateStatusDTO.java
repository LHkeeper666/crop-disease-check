package com.agriculture.modules.user.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 禁用/启用用户请求DTO
 */
@Data
public class UpdateStatusDTO {

    /**
     * 状态: ACTIVE/DISABLED
     */
    @NotBlank(message = "状态不能为空")
    @Pattern(regexp = "^(ACTIVE|DISABLED)$", message = "状态值不正确")
    private String status;
}
