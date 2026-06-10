package com.agriculture.dto;

import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

/**
 * 发送邮箱验证码请求DTO
 */
@Data
public class SendOtpDTO {

    /**
     * 邮箱
     */
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    /**
     * 验证码类型: LOGIN/REGISTER/RESET_PASSWORD
     */
    @NotBlank(message = "验证码类型不能为空")
    @Pattern(regexp = "^(LOGIN|REGISTER|RESET_PASSWORD)$", message = "验证码类型不正确")
    private String type;
}
