package com.agriculture.service;

import com.agriculture.dto.LoginByOtpDTO;
import com.agriculture.dto.LoginDTO;
import com.agriculture.dto.RegisterDTO;
import com.agriculture.dto.SendOtpDTO;
import com.agriculture.vo.LoginVO;
import com.agriculture.vo.UserVO;

/**
 * 认证服务接口
 */
public interface AuthService {

    /**
     * 用户注册
     */
    UserVO register(RegisterDTO dto);

    /**
     * 用户登录（用户名密码）
     */
    LoginVO login(LoginDTO dto);

    /**
     * 发送邮箱验证码
     */
    void sendOtp(SendOtpDTO dto);

    /**
     * 邮箱验证码登录
     */
    LoginVO loginByOtp(LoginByOtpDTO dto);

    /**
     * 用户登出
     */
    void logout(String userId, String token);

    /**
     * 刷新Token
     */
    LoginVO refreshToken(String refreshToken);
}
