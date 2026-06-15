package com.agriculture.modules.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员重置密码 DTO
 */
@Data
public class ResetPasswordDTO {

    /**
     * 新密码：6位以上，必须包含字母和数字
     */
    @Size(min = 6, max = 32, message = "密码长度6-32位")
    @Pattern(regexp = "^(?=.*[a-zA-Z])(?=.*\\d).+$", message = "密码必须包含字母和数字")
    private String newPassword;
}
