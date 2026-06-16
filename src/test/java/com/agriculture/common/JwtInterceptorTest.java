package com.agriculture.common;

import com.agriculture.common.interceptor.JwtInterceptor;
import com.agriculture.common.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("JwtInterceptor 单元测试")
class JwtInterceptorTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private StringRedisTemplate redisTemplate;

    @InjectMocks
    private JwtInterceptor jwtInterceptor;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private Object handler;

    private StringWriter responseWriter;

    @BeforeEach
    void setUp() throws Exception {
        responseWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
    }

    @Nested
    @DisplayName("preHandle - Token验证")
    class PreHandle {

        @Test
        @DisplayName("无Token时返回401")
        void preHandle_noToken_returns401() throws Exception {
            when(request.getHeader("Authorization")).thenReturn(null);

            boolean result = jwtInterceptor.preHandle(request, response, handler);

            assertFalse(result);
            verify(response).setStatus(401);
            assertTrue(responseWriter.toString().contains("未提供认证Token"));
        }

        @Test
        @DisplayName("空Token时返回401")
        void preHandle_emptyToken_returns401() throws Exception {
            when(request.getHeader("Authorization")).thenReturn("");

            boolean result = jwtInterceptor.preHandle(request, response, handler);

            assertFalse(result);
            verify(response).setStatus(401);
        }

        @Test
        @DisplayName("无效Token时返回401")
        void preHandle_invalidToken_returns401() throws Exception {
            when(request.getHeader("Authorization")).thenReturn("Bearer invalid-token");
            when(jwtUtil.validateToken("invalid-token")).thenReturn(false);

            boolean result = jwtInterceptor.preHandle(request, response, handler);

            assertFalse(result);
            verify(response).setStatus(401);
            assertTrue(responseWriter.toString().contains("Token无效或已过期"));
        }

        @Test
        @DisplayName("有效Token时放行并设置userId和username")
        void preHandle_validToken_setsAttributesAndReturns() throws Exception {
            when(request.getHeader("Authorization")).thenReturn("Bearer valid-token");
            when(jwtUtil.validateToken("valid-token")).thenReturn(true);
            when(jwtUtil.getUserIdFromToken("valid-token")).thenReturn("u-001");
            when(jwtUtil.getUsernameFromToken("valid-token")).thenReturn("admin");

            boolean result = jwtInterceptor.preHandle(request, response, handler);

            assertTrue(result);
            verify(request).setAttribute("userId", "u-001");
            verify(request).setAttribute("username", "admin");
        }

        @Test
        @DisplayName("不带Bearer前缀的Token也能处理")
        void preHandle_tokenWithoutBearerPrefix_works() throws Exception {
            when(request.getHeader("Authorization")).thenReturn("valid-token");
            when(jwtUtil.validateToken("valid-token")).thenReturn(true);
            when(jwtUtil.getUserIdFromToken("valid-token")).thenReturn("u-002");
            when(jwtUtil.getUsernameFromToken("valid-token")).thenReturn("expert");

            boolean result = jwtInterceptor.preHandle(request, response, handler);

            assertTrue(result);
            verify(request).setAttribute("userId", "u-002");
        }
    }
}
