package com.agriculture.modules.auth.controller;

import com.agriculture.modules.user.dto.LoginByOtpDTO;
import com.agriculture.modules.user.dto.LoginDTO;
import com.agriculture.modules.auth.dto.RefreshTokenDTO;
import com.agriculture.modules.user.dto.RegisterDTO;
import com.agriculture.modules.user.dto.SendOtpDTO;
import com.agriculture.modules.auth.service.AuthService;
import com.agriculture.modules.user.vo.LoginVO;
import com.agriculture.common.vo.Result;
import com.agriculture.modules.user.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * 认证控制器
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 用户登录（用户名密码）
     */
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        LoginVO loginVO = authService.login(dto);
        return Result.success("登录成功", loginVO);
    }

    /**
     * 发送邮箱验证码
     */
    @PostMapping("/send-otp")
    public Result<Void> sendOtp(@Valid @RequestBody SendOtpDTO dto) {
        authService.sendOtp(dto);
        return Result.success("验证码已发送，请查收邮箱", null);
    }

    /**
     * 邮箱验证码登录
     */
    @PostMapping("/login-by-otp")
    public Result<LoginVO> loginByOtp(@Valid @RequestBody LoginByOtpDTO dto) {
        LoginVO loginVO = authService.loginByOtp(dto);
        return Result.success("登录成功", loginVO);
    }

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public Result<UserVO> register(@Valid @RequestBody RegisterDTO dto) {
        UserVO user = authService.register(dto);
        return Result.success("注册成功", user);
    }

    /**
     * 退出登录
     */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        authService.logout(userId, token);
        return Result.success("退出成功", null);
    }

    /**
     * 刷新Token
     */
    @PostMapping("/refresh")
    public Result<LoginVO> refreshToken(@Valid @RequestBody RefreshTokenDTO dto) {
        LoginVO loginVO = authService.refreshToken(dto.getRefreshToken());
        return Result.success("Token刷新成功", loginVO);
    }
}
