package com.agriculture.controller;

import com.agriculture.dto.*;
import com.agriculture.exception.BusinessException;
import com.agriculture.exception.GlobalExceptionHandler;
import com.agriculture.service.AuthService;
import com.agriculture.vo.LoginVO;
import com.agriculture.vo.Result;
import com.agriculture.vo.UserVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 认证控制器测试
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new LocalValidatorFactoryBean())
                .build();
    }

    // ==================== 2.1 用户登录（用户名密码）====================

    @Nested
    @DisplayName("2.1 用户登录（用户名密码）")
    class Login {

        @Test
        @DisplayName("登录成功")
        void login_validCredentials_success() throws Exception {
            LoginDTO dto = new LoginDTO();
            dto.setUsername("admin");
            dto.setPassword("admin123");

            UserVO userVO = new UserVO();
            userVO.setId("user-001");
            userVO.setUsername("admin");
            userVO.setName("管理员");
            userVO.setRole("ADMIN");

            LoginVO loginVO = new LoginVO();
            loginVO.setToken("eyJhbGciOiJIUzI1NiJ9.token");
            loginVO.setRefreshToken("eyJhbGciOiJIUzI1NiJ9.refresh");
            loginVO.setExpiresIn(86400L);
            loginVO.setUserInfo(userVO);

            when(authService.login(any(LoginDTO.class))).thenReturn(loginVO);

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("登录成功"))
                    .andExpect(jsonPath("$.data.token").value("eyJhbGciOiJIUzI1NiJ9.token"))
                    .andExpect(jsonPath("$.data.refreshToken").value("eyJhbGciOiJIUzI1NiJ9.refresh"))
                    .andExpect(jsonPath("$.data.expiresIn").value(86400))
                    .andExpect(jsonPath("$.data.userInfo.username").value("admin"))
                    .andExpect(jsonPath("$.data.userInfo.role").value("ADMIN"));
        }

        @Test
        @DisplayName("用户名为空时返回400")
        void login_emptyUsername_returns400() throws Exception {
            LoginDTO dto = new LoginDTO();
            dto.setUsername("");
            dto.setPassword("admin123");

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("密码为空时返回400")
        void login_emptyPassword_returns400() throws Exception {
            LoginDTO dto = new LoginDTO();
            dto.setUsername("admin");
            dto.setPassword("");

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("用户名或密码错误时返回错误")
        void login_wrongPassword_returnsError() throws Exception {
            LoginDTO dto = new LoginDTO();
            dto.setUsername("admin");
            dto.setPassword("wrong");

            when(authService.login(any(LoginDTO.class)))
                    .thenThrow(new BusinessException("用户名或密码错误"));

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.message").value("用户名或密码错误"));
        }

        @Test
        @DisplayName("账号被禁用时返回错误")
        void login_disabledAccount_returnsError() throws Exception {
            LoginDTO dto = new LoginDTO();
            dto.setUsername("disabled_user");
            dto.setPassword("admin123");

            when(authService.login(any(LoginDTO.class)))
                    .thenThrow(new BusinessException("账号已被禁用"));

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.message").value("账号已被禁用"));
        }
    }

    // ==================== 2.2 发送邮箱验证码 ====================

    @Nested
    @DisplayName("2.2 发送邮箱验证码")
    class SendOtp {

        @Test
        @DisplayName("发送验证码成功")
        void sendOtp_validEmail_success() throws Exception {
            SendOtpDTO dto = new SendOtpDTO();
            dto.setEmail("2043412933@qq.com");
            dto.setType("LOGIN");

            doNothing().when(authService).sendOtp(any(SendOtpDTO.class));

            mockMvc.perform(post("/api/auth/send-otp")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("验证码已发送，请查收邮箱"));

            verify(authService, times(1)).sendOtp(any(SendOtpDTO.class));
        }

        @Test
        @DisplayName("邮箱格式不正确时返回400")
        void sendOtp_invalidEmail_returns400() throws Exception {
            SendOtpDTO dto = new SendOtpDTO();
            dto.setEmail("not-an-email");
            dto.setType("LOGIN");

            mockMvc.perform(post("/api/auth/send-otp")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("邮箱为空时返回400")
        void sendOtp_emptyEmail_returns400() throws Exception {
            SendOtpDTO dto = new SendOtpDTO();
            dto.setEmail("");
            dto.setType("LOGIN");

            mockMvc.perform(post("/api/auth/send-otp")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("验证码类型不正确时返回400")
        void sendOtp_invalidType_returns400() throws Exception {
            SendOtpDTO dto = new SendOtpDTO();
            dto.setEmail("2043412933@qq.com");
            dto.setType("INVALID");

            mockMvc.perform(post("/api/auth/send-otp")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("发送过于频繁时返回错误")
        void sendOtp_tooFrequent_returnsError() throws Exception {
            SendOtpDTO dto = new SendOtpDTO();
            dto.setEmail("2043412933@qq.com");
            dto.setType("LOGIN");

            doThrow(new BusinessException("验证码发送过于频繁，请1分钟后重试"))
                    .when(authService).sendOtp(any(SendOtpDTO.class));

            mockMvc.perform(post("/api/auth/send-otp")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.message").value("验证码发送过于频繁，请1分钟后重试"));
        }
    }

    // ==================== 2.3 邮箱验证码登录 ====================

    @Nested
    @DisplayName("2.3 邮箱验证码登录")
    class LoginByOtp {

        @Test
        @DisplayName("验证码登录成功")
        void loginByOtp_validOtp_success() throws Exception {
            LoginByOtpDTO dto = new LoginByOtpDTO();
            dto.setEmail("2043412933@qq.com");
            dto.setOtp("123456");

            UserVO userVO = new UserVO();
            userVO.setId("user-001");
            userVO.setUsername("test");
            userVO.setEmail("2043412933@qq.com");

            LoginVO loginVO = new LoginVO();
            loginVO.setToken("eyJhbGciOiJIUzI1NiJ9.token");
            loginVO.setRefreshToken("eyJhbGciOiJIUzI1NiJ9.refresh");
            loginVO.setExpiresIn(86400L);
            loginVO.setUserInfo(userVO);

            when(authService.loginByOtp(any(LoginByOtpDTO.class))).thenReturn(loginVO);

            mockMvc.perform(post("/api/auth/login-by-otp")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("登录成功"))
                    .andExpect(jsonPath("$.data.token").isNotEmpty())
                    .andExpect(jsonPath("$.data.userInfo.email").value("2043412933@qq.com"));
        }

        @Test
        @DisplayName("验证码过期时返回错误")
        void loginByOtp_expiredOtp_returnsError() throws Exception {
            LoginByOtpDTO dto = new LoginByOtpDTO();
            dto.setEmail("2043412933@qq.com");
            dto.setOtp("123456");

            when(authService.loginByOtp(any(LoginByOtpDTO.class)))
                    .thenThrow(new BusinessException("验证码已过期，请重新获取"));

            mockMvc.perform(post("/api/auth/login-by-otp")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.message").value("验证码已过期，请重新获取"));
        }

        @Test
        @DisplayName("验证码错误时返回错误")
        void loginByOtp_wrongOtp_returnsError() throws Exception {
            LoginByOtpDTO dto = new LoginByOtpDTO();
            dto.setEmail("2043412933@qq.com");
            dto.setOtp("000000");

            when(authService.loginByOtp(any(LoginByOtpDTO.class)))
                    .thenThrow(new BusinessException("验证码错误"));

            mockMvc.perform(post("/api/auth/login-by-otp")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.message").value("验证码错误"));
        }

        @Test
        @DisplayName("邮箱格式不正确时返回400")
        void loginByOtp_invalidEmail_returns400() throws Exception {
            LoginByOtpDTO dto = new LoginByOtpDTO();
            dto.setEmail("not-an-email");
            dto.setOtp("123456");

            mockMvc.perform(post("/api/auth/login-by-otp")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("验证码位数不对时返回400")
        void loginByOtp_invalidOtpLength_returns400() throws Exception {
            LoginByOtpDTO dto = new LoginByOtpDTO();
            dto.setEmail("2043412933@qq.com");
            dto.setOtp("123");

            mockMvc.perform(post("/api/auth/login-by-otp")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ==================== 2.4 用户注册 ====================

    @Nested
    @DisplayName("2.4 用户注册")
    class Register {

        @Test
        @DisplayName("注册成功 - test用户发送邮件至2043412933@qq.com")
        void register_validData_success() throws Exception {
            RegisterDTO dto = new RegisterDTO();
            dto.setUsername("test");
            dto.setPassword("test123");
            dto.setEmail("2043412933@qq.com");

            UserVO userVO = new UserVO();
            userVO.setId("user-new-001");
            userVO.setUsername("test");
            userVO.setEmail("2043412933@qq.com");
            userVO.setRole("VISITOR");
            userVO.setStatus("ACTIVE");
            userVO.setCreatedAt(LocalDateTime.of(2026, 6, 11, 12, 0, 0));

            when(authService.register(any(RegisterDTO.class))).thenReturn(userVO);

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("注册成功"))
                    .andExpect(jsonPath("$.data.username").value("test"))
                    .andExpect(jsonPath("$.data.email").value("2043412933@qq.com"))
                    .andExpect(jsonPath("$.data.role").value("VISITOR"))
                    .andExpect(jsonPath("$.data.status").value("ACTIVE"));

            verify(authService, times(1)).register(any(RegisterDTO.class));
        }

        @Test
        @DisplayName("用户名为空时返回400")
        void register_emptyUsername_returns400() throws Exception {
            RegisterDTO dto = new RegisterDTO();
            dto.setUsername("");
            dto.setPassword("test123");
            dto.setEmail("2043412933@qq.com");

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("密码为空时返回400")
        void register_emptyPassword_returns400() throws Exception {
            RegisterDTO dto = new RegisterDTO();
            dto.setUsername("test");
            dto.setPassword("");
            dto.setEmail("2043412933@qq.com");

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("邮箱格式不正确时返回400")
        void register_invalidEmail_returns400() throws Exception {
            RegisterDTO dto = new RegisterDTO();
            dto.setUsername("test");
            dto.setPassword("test123");
            dto.setEmail("not-an-email");

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("用户名已存在时返回错误")
        void register_duplicateUsername_returnsError() throws Exception {
            RegisterDTO dto = new RegisterDTO();
            dto.setUsername("existing_user");
            dto.setPassword("test123");
            dto.setEmail("2043412933@qq.com");

            when(authService.register(any(RegisterDTO.class)))
                    .thenThrow(new BusinessException("用户名已存在"));

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.message").value("用户名已存在"));
        }

        @Test
        @DisplayName("注册带手机号和姓名")
        void register_withNameAndPhone_success() throws Exception {
            RegisterDTO dto = new RegisterDTO();
            dto.setUsername("test");
            dto.setPassword("test123");
            dto.setName("测试用户");
            dto.setPhone("13800138000");
            dto.setEmail("2043412933@qq.com");

            UserVO userVO = new UserVO();
            userVO.setId("user-new-002");
            userVO.setUsername("test");
            userVO.setName("测试用户");
            userVO.setPhone("13800138000");
            userVO.setEmail("2043412933@qq.com");
            userVO.setRole("VISITOR");

            when(authService.register(any(RegisterDTO.class))).thenReturn(userVO);

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.name").value("测试用户"))
                    .andExpect(jsonPath("$.data.phone").value("13800138000"));
        }
    }

    // ==================== 2.5 退出登录 ====================

    @Nested
    @DisplayName("2.5 退出登录")
    class Logout {

        @Test
        @DisplayName("退出登录成功")
        void logout_withToken_success() throws Exception {
            doNothing().when(authService).logout(anyString(), anyString());

            mockMvc.perform(post("/api/auth/logout")
                            .header("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.token")
                            .requestAttr("userId", "user-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("退出成功"));

            verify(authService, times(1)).logout(eq("user-001"), eq("eyJhbGciOiJIUzI1NiJ9.token"));
        }

        @Test
        @DisplayName("无Token时也能退出")
        void logout_noToken_success() throws Exception {
            doNothing().when(authService).logout(any(), any());

            mockMvc.perform(post("/api/auth/logout"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("退出成功"));
        }
    }

    // ==================== 2.6 刷新Token ====================

    @Nested
    @DisplayName("2.6 刷新Token")
    class RefreshToken {

        @Test
        @DisplayName("刷新Token成功")
        void refreshToken_validToken_success() throws Exception {
            RefreshTokenDTO dto = new RefreshTokenDTO();
            dto.setRefreshToken("eyJhbGciOiJIUzI1NiJ9.valid-refresh");

            UserVO userVO = new UserVO();
            userVO.setId("user-001");
            userVO.setUsername("admin");

            LoginVO loginVO = new LoginVO();
            loginVO.setToken("eyJhbGciOiJIUzI1NiJ9.new-token");
            loginVO.setRefreshToken("eyJhbGciOiJIUzI1NiJ9.new-refresh");
            loginVO.setExpiresIn(86400L);
            loginVO.setUserInfo(userVO);

            when(authService.refreshToken(anyString())).thenReturn(loginVO);

            mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("Token刷新成功"))
                    .andExpect(jsonPath("$.data.token").value("eyJhbGciOiJIUzI1NiJ9.new-token"))
                    .andExpect(jsonPath("$.data.refreshToken").value("eyJhbGciOiJIUzI1NiJ9.new-refresh"));
        }

        @Test
        @DisplayName("refreshToken为空时返回400")
        void refreshToken_emptyToken_returns400() throws Exception {
            RefreshTokenDTO dto = new RefreshTokenDTO();
            dto.setRefreshToken("");

            mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Token无效时返回错误")
        void refreshToken_invalidToken_returnsError() throws Exception {
            RefreshTokenDTO dto = new RefreshTokenDTO();
            dto.setRefreshToken("invalid-token");

            when(authService.refreshToken(anyString()))
                    .thenThrow(new BusinessException("RefreshToken无效或已过期"));

            mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.message").value("RefreshToken无效或已过期"));
        }
    }
}
