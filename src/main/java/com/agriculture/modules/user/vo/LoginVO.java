package com.agriculture.modules.user.vo;

import lombok.Data;

/**
 * 登录返回VO
 */
@Data
public class LoginVO {

    /**
     * JWT AccessToken
     */
    private String token;

    /**
     * JWT RefreshToken
     */
    private String refreshToken;

    /**
     * Token过期时间（秒）
     */
    private Long expiresIn;

    /**
     * 用户信息
     */
    private UserVO userInfo;
}
