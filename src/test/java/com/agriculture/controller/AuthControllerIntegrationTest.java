package com.agriculture.controller;

import com.agriculture.modules.user.dto.RegisterDTO;
import com.agriculture.modules.user.dto.SendOtpDTO;
import com.agriculture.modules.user.dto.LoginDTO;
import com.agriculture.modules.auth.service.AuthService;
import com.agriculture.modules.user.vo.LoginVO;
import com.agriculture.modules.user.vo.UserVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.agriculture.modules.user.mapper.SysUserMapper;
import com.agriculture.modules.user.entity.SysUser;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 认证模块集成测试
 * 连接真实数据库(MySQL)、缓存(Redis)、邮件服务(SMTP)
 *
 * 运行前请确保:
 * 1. 本地 MySQL 3306 端口可用, 数据库 agriculture_db 已创建
 * 2. Redis 6379 端口可用 (密码 agri_redis_2026)
 * 3. QQ邮箱 SMTP 服务可用
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthControllerIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String TEST_USERNAME = "test";
    private static final String TEST_PASSWORD = "test123";
    private static final String TEST_EMAIL = "2043412933@qq.com";

    /**
     * 清理测试数据 - 用原生SQL硬删除，绕过MyBatis-Plus逻辑删除
     */
    @BeforeEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM sys_user WHERE username = ?", TEST_USERNAME);
        jdbcTemplate.update("DELETE FROM sys_user WHERE email = ?", TEST_EMAIL);
    }

    // ==================== 注册 → 数据库持久化 ====================

    @Test
    @Order(1)
    @DisplayName("注册用户 - 验证数据库持久化")
    void register_persistToDatabase() {
        // given
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername(TEST_USERNAME);
        dto.setPassword(TEST_PASSWORD);
        dto.setEmail(TEST_EMAIL);

        // when
        UserVO result = authService.register(dto);

        // then - 返回值校验
        assertNotNull(result.getId(), "用户ID不应为空");
        assertEquals(TEST_USERNAME, result.getUsername());
        assertEquals(TEST_EMAIL, result.getEmail());
        assertEquals("VISITOR", result.getRole());
        assertEquals("ACTIVE", result.getStatus());

        // then - 数据库校验
        SysUser dbUser = userMapper.selectById(result.getId());
        assertNotNull(dbUser, "数据库中应存在该用户");
        assertEquals(TEST_USERNAME, dbUser.getUsername());
        assertEquals(TEST_EMAIL, dbUser.getEmail());
        assertEquals("VISITOR", dbUser.getRole());
        assertEquals("ACTIVE", dbUser.getStatus());
        assertNotNull(dbUser.getPassword(), "密码不应为空");
        assertNotEquals(TEST_PASSWORD, dbUser.getPassword(), "密码应为BCrypt加密存储");
        assertTrue(dbUser.getPassword().startsWith("$2a$"), "密码应为BCrypt格式");

        System.out.println("✅ 注册成功, 用户ID: " + result.getId());
        System.out.println("✅ 数据库验证通过, 用户名: " + dbUser.getUsername());
        System.out.println("✅ 密码已BCrypt加密: " + dbUser.getPassword().substring(0, 20) + "...");
    }

    // ==================== 发送验证码 → 邮件 + Redis ====================

    @Test
    @Order(2)
    @DisplayName("发送验证码 - 验证邮件发送和Redis存储")
    void sendOtp_sendEmail_andStoreInRedis() {
        // given
        SendOtpDTO dto = new SendOtpDTO();
        dto.setEmail(TEST_EMAIL);
        dto.setType("LOGIN");

        // 清理可能残留的Redis key
        redisTemplate.delete("otp:" + TEST_EMAIL + ":LOGIN");
        redisTemplate.delete("otp_interval:" + TEST_EMAIL + ":LOGIN");

        // when - 这会真正发送邮件
        assertDoesNotThrow(() -> authService.sendOtp(dto), "发送验证码不应抛异常");

        // then - 验证Redis中存储了验证码
        String cachedOtp = redisTemplate.opsForValue().get("otp:" + TEST_EMAIL + ":LOGIN");
        assertNotNull(cachedOtp, "Redis中应存储验证码");
        assertEquals(6, cachedOtp.length(), "验证码应为6位");
        assertTrue(cachedOtp.matches("\\d{6}"), "验证码应为纯数字");

        // then - 验证发送间隔key已设置
        Boolean hasInterval = redisTemplate.hasKey("otp_interval:" + TEST_EMAIL + ":LOGIN");
        assertTrue(Boolean.TRUE.equals(hasInterval), "发送间隔key应存在");

        System.out.println("✅ 验证码已发送至: " + TEST_EMAIL);
        System.out.println("✅ Redis中的验证码: " + cachedOtp);
        System.out.println("✅ 请检查邮箱收件箱确认邮件已送达");
    }

    // ==================== 登录 → 完整流程 ====================

    @Test
    @Order(3)
    @DisplayName("登录 - 验证密码校验和Token生成")
    void login_verifyPassword_andGenerateToken() {
        // given - 先注册
        RegisterDTO registerDTO = new RegisterDTO();
        registerDTO.setUsername(TEST_USERNAME);
        registerDTO.setPassword(TEST_PASSWORD);
        registerDTO.setEmail(TEST_EMAIL);
        UserVO registered = authService.register(registerDTO);

        // when - 用正确密码登录
        LoginDTO loginDTO = new LoginDTO();
        loginDTO.setUsername(TEST_USERNAME);
        loginDTO.setPassword(TEST_PASSWORD);
        LoginVO loginVO = authService.login(loginDTO);

        // then
        assertNotNull(loginVO.getToken(), "Token不应为空");
        assertNotNull(loginVO.getRefreshToken(), "RefreshToken不应为空");
        assertEquals(86400L, loginVO.getExpiresIn());
        assertEquals(TEST_USERNAME, loginVO.getUserInfo().getUsername());
        assertEquals(registered.getId(), loginVO.getUserInfo().getId());

        System.out.println("✅ 登录成功");
        System.out.println("✅ Token: " + loginVO.getToken().substring(0, 30) + "...");
        System.out.println("✅ 用户: " + loginVO.getUserInfo().getUsername());
    }

    @Test
    @Order(4)
    @DisplayName("登录 - 错误密码应抛异常")
    void login_wrongPassword_shouldThrow() {
        // given - 先注册
        RegisterDTO registerDTO = new RegisterDTO();
        registerDTO.setUsername(TEST_USERNAME);
        registerDTO.setPassword(TEST_PASSWORD);
        registerDTO.setEmail(TEST_EMAIL);
        authService.register(registerDTO);

        // when + then
        LoginDTO loginDTO = new LoginDTO();
        loginDTO.setUsername(TEST_USERNAME);
        loginDTO.setPassword("wrong_password");

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> authService.login(loginDTO));
        assertTrue(exception.getMessage().contains("密码错误") || exception.getMessage().contains("用户名或密码错误"));

        System.out.println("✅ 错误密码正确抛出异常: " + exception.getMessage());
    }

    // ==================== 重复注册 ====================

    @Test
    @Order(5)
    @DisplayName("重复注册 - 应抛出用户名已存在异常")
    void register_duplicateUsername_shouldThrow() {
        // given - 先注册一次
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername(TEST_USERNAME);
        dto.setPassword(TEST_PASSWORD);
        dto.setEmail(TEST_EMAIL);
        authService.register(dto);

        // when + then - 再注册同名用户
        RegisterDTO duplicate = new RegisterDTO();
        duplicate.setUsername(TEST_USERNAME);
        duplicate.setPassword("another_pwd");
        duplicate.setEmail("another@qq.com");

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> authService.register(duplicate));
        assertTrue(exception.getMessage().contains("已存在"));

        System.out.println("✅ 重复注册正确抛出异常: " + exception.getMessage());
    }

    // ==================== 数据清理 ====================

    // @AfterEach
    // void tearDown() {
    //     // 硬删除测试用户
    //     jdbcTemplate.update("DELETE FROM sys_user WHERE username = ?", TEST_USERNAME);
    //
    //     // 清理Redis
    //     redisTemplate.delete("otp:" + TEST_EMAIL + ":LOGIN");
    //     redisTemplate.delete("otp_interval:" + TEST_EMAIL + ":LOGIN");
    // }
}
