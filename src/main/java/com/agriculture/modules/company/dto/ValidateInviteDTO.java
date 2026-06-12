package com.agriculture.modules.company.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 验证邀请码请求DTO
 */
@Data
public class ValidateInviteDTO {

    /**
     * 邀请码
     */
    @NotBlank(message = "邀请码不能为空")
    private String inviteCode;
}
