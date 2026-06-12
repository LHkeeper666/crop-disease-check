package com.agriculture.modules.company.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 通过邀请码加入企业请求DTO
 */
@Data
public class JoinCompanyDTO {

    /**
     * 邀请码
     */
    @NotBlank(message = "邀请码不能为空")
    private String inviteCode;
}
