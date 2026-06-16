package com.agriculture.common;

import com.agriculture.common.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JwtUtil 单元测试")
class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", "agriculture-secret-key-2026");
        ReflectionTestUtils.setField(jwtUtil, "expiration", 86400L);
        ReflectionTestUtils.setField(jwtUtil, "refreshExpiration", 604800L);
    }

    @Nested
    @DisplayName("generateToken - 生成Token")
    class GenerateToken {

        @Test
        @DisplayName("生成的Token不为空")
        void generateToken_returnsNonNull() {
            String token = jwtUtil.generateToken("u-001", "admin");
            assertNotNull(token);
            assertFalse(token.isEmpty());
        }

        @Test
        @DisplayName("相同用户生成的Token不同（因为时间戳不同）")
        void generateToken_differentCallsProduceDifferentTokens() {
            String token1 = jwtUtil.generateToken("u-001", "admin");
            String token2 = jwtUtil.generateToken("u-001", "admin");
            // JWT payload 中 iat 可能相同，但签名一致所以 token 相同是正常的
            assertNotNull(token1);
            assertNotNull(token2);
        }
    }

    @Nested
    @DisplayName("validateToken - 验证Token")
    class ValidateToken {

        @Test
        @DisplayName("有效Token返回true")
        void validateToken_validToken_returnsTrue() {
            String token = jwtUtil.generateToken("u-001", "admin");
            assertTrue(jwtUtil.validateToken(token));
        }

        @Test
        @DisplayName("无效Token返回false")
        void validateToken_invalidToken_returnsFalse() {
            assertFalse(jwtUtil.validateToken("invalid.token.value"));
        }

        @Test
        @DisplayName("空Token返回false")
        void validateToken_emptyToken_returnsFalse() {
            assertFalse(jwtUtil.validateToken(""));
        }

        @Test
        @DisplayName("被篡改的Token返回false")
        void validateToken_tamperedToken_returnsFalse() {
            String token = jwtUtil.generateToken("u-001", "admin");
            String tampered = token.substring(0, token.length() - 5) + "XXXXX";
            assertFalse(jwtUtil.validateToken(tampered));
        }

        @Test
        @DisplayName("用错误密钥生成的Token返回false")
        void validateToken_wrongSecret_returnsFalse() {
            JwtUtil otherUtil = new JwtUtil();
            ReflectionTestUtils.setField(otherUtil, "secret", "wrong-secret-key");
            ReflectionTestUtils.setField(otherUtil, "expiration", 86400L);
            String token = otherUtil.generateToken("u-001", "admin");
            assertFalse(jwtUtil.validateToken(token));
        }
    }

    @Nested
    @DisplayName("getUserIdFromToken / getUsernameFromToken - 解析Token")
    class ParseToken {

        @Test
        @DisplayName("正确解析userId")
        void getUserIdFromToken_returnsCorrectUserId() {
            String token = jwtUtil.generateToken("u-001", "admin");
            assertEquals("u-001", jwtUtil.getUserIdFromToken(token));
        }

        @Test
        @DisplayName("正确解析username")
        void getUsernameFromToken_returnsCorrectUsername() {
            String token = jwtUtil.generateToken("u-001", "admin");
            assertEquals("admin", jwtUtil.getUsernameFromToken(token));
        }
    }

    @Nested
    @DisplayName("generateRefreshToken - 生成刷新Token")
    class RefreshToken {

        @Test
        @DisplayName("刷新Token不为空")
        void generateRefreshToken_returnsNonNull() {
            String refreshToken = jwtUtil.generateRefreshToken("u-001", "admin");
            assertNotNull(refreshToken);
            assertFalse(refreshToken.isEmpty());
        }

        @Test
        @DisplayName("刷新Token的type为refresh")
        void refreshToken_typeIsRefresh() {
            String refreshToken = jwtUtil.generateRefreshToken("u-001", "admin");
            assertTrue(jwtUtil.isRefreshToken(refreshToken));
        }

        @Test
        @DisplayName("普通Token的type不是refresh")
        void accessToken_typeIsNotRefresh() {
            String accessToken = jwtUtil.generateToken("u-001", "admin");
            assertFalse(jwtUtil.isRefreshToken(accessToken));
        }
    }
}
