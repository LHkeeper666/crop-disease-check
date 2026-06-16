package com.agriculture.service;

import cn.hutool.crypto.digest.BCrypt;
import com.agriculture.common.exception.BusinessException;
import com.agriculture.common.service.EmailService;
import com.agriculture.common.util.JwtUtil;
import com.agriculture.modules.auth.service.impl.AuthServiceImpl;
import com.agriculture.modules.user.dto.LoginByOtpDTO;
import com.agriculture.modules.user.dto.LoginDTO;
import com.agriculture.modules.user.dto.RegisterDTO;
import com.agriculture.modules.user.dto.SendOtpDTO;
import com.agriculture.modules.user.entity.SysUser;
import com.agriculture.modules.user.mapper.SysUserMapper;
import com.agriculture.modules.user.vo.LoginVO;
import com.agriculture.modules.user.vo.UserVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AuthServiceImpl 单元测试")
class AuthServiceImplTest {

    @Mock private SysUserMapper userMapper;
    @Mock private JwtUtil jwtUtil;
    @Mock private EmailService emailService;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private AuthServiceImpl authService;

    private SysUser activeUser;

    @BeforeEach
    void setUp() {
        activeUser = new SysUser();
        activeUser.setId("u-001");
        activeUser.setUsername("admin");
        activeUser.setPassword(BCrypt.hashpw("admin123"));
        activeUser.setName("系统管理员");
        activeUser.setEmail("admin@agriculture.com");
        activeUser.setRole("ADMIN");
        activeUser.setStatus("ACTIVE");
        activeUser.setDeleted((byte) 0);
        activeUser.setCreatedAt(LocalDateTime.now());
        activeUser.setUpdatedAt(LocalDateTime.now());

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    // ==================== login ====================

    @Nested
    @DisplayName("login - 用户名密码登录")
    class Login {

        @Test
        @DisplayName("登录成功返回Token和用户信息")
        void login_validCredentials_returnsLoginVO() {
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(activeUser);
            when(jwtUtil.generateToken("u-001", "admin")).thenReturn("access-token");
            when(jwtUtil.generateRefreshToken("u-001", "admin")).thenReturn("refresh-token");

            LoginDTO dto = new LoginDTO();
            dto.setUsername("admin");
            dto.setPassword("admin123");

            LoginVO result = authService.login(dto);

            assertEquals("access-token", result.getToken());
            assertEquals("refresh-token", result.getRefreshToken());
            assertEquals(86400L, result.getExpiresIn());
            assertEquals("u-001", result.getUserInfo().getId());
        }

        @Test
        @DisplayName("用户不存在抛异常")
        void login_userNotFound_throwsException() {
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            LoginDTO dto = new LoginDTO();
            dto.setUsername("nonexist");
            dto.setPassword("admin123");

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.login(dto));
            assertTrue(ex.getMessage().contains("用户名或密码错误"));
        }

        @Test
        @DisplayName("密码错误抛异常")
        void login_wrongPassword_throwsException() {
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(activeUser);

            LoginDTO dto = new LoginDTO();
            dto.setUsername("admin");
            dto.setPassword("wrong-password");

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.login(dto));
            assertTrue(ex.getMessage().contains("用户名或密码错误"));
        }

        @Test
        @DisplayName("账号禁用抛异常")
        void login_disabledAccount_throwsException() {
            activeUser.setStatus("DISABLED");
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(activeUser);

            LoginDTO dto = new LoginDTO();
            dto.setUsername("admin");
            dto.setPassword("admin123");

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.login(dto));
            assertTrue(ex.getMessage().contains("账号已被禁用"));
        }
    }

    // ==================== register ====================

    @Nested
    @DisplayName("register - 用户注册")
    class Register {

        @Test
        @DisplayName("注册成功返回用户信息")
        void register_validData_returnsUserVO() {
            when(valueOperations.get("otp:test@qq.com:REGISTER")).thenReturn("123456");
            when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            when(userMapper.insert(any(SysUser.class))).thenReturn(1);

            RegisterDTO dto = new RegisterDTO();
            dto.setEmail("test@qq.com");
            dto.setUsername("newuser");
            dto.setPassword("password123");
            dto.setCode("123456");

            UserVO result = authService.register(dto);

            assertNotNull(result);
            assertEquals("newuser", result.getUsername());
            verify(redisTemplate).delete("otp:test@qq.com:REGISTER");
        }

        @Test
        @DisplayName("验证码过期抛异常")
        void register_expiredOtp_throwsException() {
            when(valueOperations.get("otp:test@qq.com:REGISTER")).thenReturn(null);

            RegisterDTO dto = new RegisterDTO();
            dto.setEmail("test@qq.com");
            dto.setUsername("newuser");
            dto.setPassword("password123");
            dto.setCode("123456");

            assertThrows(BusinessException.class, () -> authService.register(dto));
        }

        @Test
        @DisplayName("验证码错误抛异常")
        void register_wrongOtp_throwsException() {
            when(valueOperations.get("otp:test@qq.com:REGISTER")).thenReturn("123456");

            RegisterDTO dto = new RegisterDTO();
            dto.setEmail("test@qq.com");
            dto.setUsername("newuser");
            dto.setPassword("password123");
            dto.setCode("999999");

            assertThrows(BusinessException.class, () -> authService.register(dto));
        }

        @Test
        @DisplayName("邮箱已注册抛异常")
        void register_duplicateEmail_throwsException() {
            when(valueOperations.get("otp:test@qq.com:REGISTER")).thenReturn("123456");
            when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

            RegisterDTO dto = new RegisterDTO();
            dto.setEmail("test@qq.com");
            dto.setUsername("newuser");
            dto.setPassword("password123");
            dto.setCode("123456");

            assertThrows(BusinessException.class, () -> authService.register(dto));
        }
    }

    // ==================== sendOtp ====================

    @Nested
    @DisplayName("sendOtp - 发送验证码")
    class SendOtp {

        @Test
        @DisplayName("发送成功")
        void sendOtp_validEmail_sendsEmail() {
            when(redisTemplate.hasKey("otp_interval:test@qq.com:REGISTER")).thenReturn(false);

            SendOtpDTO dto = new SendOtpDTO();
            dto.setEmail("test@qq.com");
            dto.setType("REGISTER");

            authService.sendOtp(dto);

            verify(emailService).sendOtpEmail(eq("test@qq.com"), anyString(), eq("REGISTER"));
            verify(valueOperations).set(eq("otp:test@qq.com:REGISTER"), anyString(), eq(5L), eq(TimeUnit.MINUTES));
        }

        @Test
        @DisplayName("发送过于频繁抛异常")
        void sendOtp_tooFrequent_throwsException() {
            when(redisTemplate.hasKey("otp_interval:test@qq.com:REGISTER")).thenReturn(true);

            SendOtpDTO dto = new SendOtpDTO();
            dto.setEmail("test@qq.com");
            dto.setType("REGISTER");

            assertThrows(BusinessException.class, () -> authService.sendOtp(dto));
        }
    }

    // ==================== loginByOtp ====================

    @Nested
    @DisplayName("loginByOtp - 验证码登录")
    class LoginByOtp {

        @Test
        @DisplayName("验证码登录成功")
        void loginByOtp_validOtp_returnsLoginVO() {
            when(valueOperations.get("otp:test@qq.com:LOGIN")).thenReturn("123456");
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(activeUser);
            when(jwtUtil.generateToken("u-001", "admin")).thenReturn("access-token");
            when(jwtUtil.generateRefreshToken("u-001", "admin")).thenReturn("refresh-token");

            LoginByOtpDTO dto = new LoginByOtpDTO();
            dto.setEmail("test@qq.com");
            dto.setOtp("123456");

            LoginVO result = authService.loginByOtp(dto);

            assertEquals("access-token", result.getToken());
            verify(redisTemplate).delete("otp:test@qq.com:LOGIN");
        }

        @Test
        @DisplayName("验证码过期抛异常")
        void loginByOtp_expiredOtp_throwsException() {
            when(valueOperations.get("otp:test@qq.com:LOGIN")).thenReturn(null);

            LoginByOtpDTO dto = new LoginByOtpDTO();
            dto.setEmail("test@qq.com");
            dto.setOtp("123456");

            assertThrows(BusinessException.class, () -> authService.loginByOtp(dto));
        }

        @Test
        @DisplayName("邮箱未注册抛异常")
        void loginByOtp_emailNotRegistered_throwsException() {
            when(valueOperations.get("otp:test@qq.com:LOGIN")).thenReturn("123456");
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            LoginByOtpDTO dto = new LoginByOtpDTO();
            dto.setEmail("test@qq.com");
            dto.setOtp("123456");

            assertThrows(BusinessException.class, () -> authService.loginByOtp(dto));
        }
    }

    // ==================== refreshToken ====================

    @Nested
    @DisplayName("refreshToken - 刷新Token")
    class RefreshToken {

        @Test
        @DisplayName("刷新成功返回新Token")
        void refreshToken_validRefreshToken_returnsNewTokens() {
            when(jwtUtil.validateToken("refresh-token")).thenReturn(true);
            when(jwtUtil.isRefreshToken("refresh-token")).thenReturn(true);
            when(jwtUtil.getUserIdFromToken("refresh-token")).thenReturn("u-001");
            when(jwtUtil.getUsernameFromToken("refresh-token")).thenReturn("admin");
            when(userMapper.selectById("u-001")).thenReturn(activeUser);
            when(jwtUtil.generateToken("u-001", "admin")).thenReturn("new-access-token");
            when(jwtUtil.generateRefreshToken("u-001", "admin")).thenReturn("new-refresh-token");

            LoginVO result = authService.refreshToken("refresh-token");

            assertEquals("new-access-token", result.getToken());
            assertEquals("new-refresh-token", result.getRefreshToken());
        }

        @Test
        @DisplayName("无效RefreshToken抛异常")
        void refreshToken_invalidToken_throwsException() {
            when(jwtUtil.validateToken("invalid")).thenReturn(false);

            assertThrows(BusinessException.class,
                    () -> authService.refreshToken("invalid"));
        }

        @Test
        @DisplayName("非RefreshToken类型抛异常")
        void refreshToken_notRefreshType_throwsException() {
            when(jwtUtil.validateToken("access-token")).thenReturn(true);
            when(jwtUtil.isRefreshToken("access-token")).thenReturn(false);

            assertThrows(BusinessException.class,
                    () -> authService.refreshToken("access-token"));
        }
    }
}
