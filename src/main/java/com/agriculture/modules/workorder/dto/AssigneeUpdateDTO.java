package com.agriculture.modules.workorder.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 更新指派专家请求DTO
 */
@Getter
@Setter
public class AssigneeUpdateDTO {

    /**
     * 指派专家用户ID
     */
    @NotBlank(message = "指派专家不能为空")
    private String assignedTo;
}
