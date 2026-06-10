package com.agriculture.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 刷新Token请求DTO
 */
@Data
public class RefreshTokenDTO {

    /**
     * 刷新Token
     */
    @NotBlank(message = "refreshToken不能为空")
    private String refreshToken;
}
