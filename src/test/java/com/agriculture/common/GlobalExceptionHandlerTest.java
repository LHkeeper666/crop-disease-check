package com.agriculture.common;

import com.agriculture.common.exception.BusinessException;
import com.agriculture.common.exception.GlobalExceptionHandler;
import com.agriculture.common.vo.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("GlobalExceptionHandler 单元测试")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Nested
    @DisplayName("handleBusinessException - 业务异常")
    class HandleBusinessException {

        @Test
        @DisplayName("默认code=500的业务异常")
        void handleBusinessException_defaultCode_returns500() {
            BusinessException ex = new BusinessException("操作失败");
            Result<?> result = handler.handleBusinessException(ex);

            assertEquals(500, result.getCode());
            assertEquals("操作失败", result.getMessage());
        }

        @Test
        @DisplayName("自定义code的业务异常")
        void handleBusinessException_customCode_returnsCustomCode() {
            BusinessException ex = new BusinessException(40090, "邀请码不存在");
            Result<?> result = handler.handleBusinessException(ex);

            assertEquals(40090, result.getCode());
            assertEquals("邀请码不存在", result.getMessage());
        }

        @Test
        @DisplayName("401未认证异常")
        void handleBusinessException_401_returns401() {
            BusinessException ex = new BusinessException(401, "用户未登录");
            Result<?> result = handler.handleBusinessException(ex);

            assertEquals(401, result.getCode());
            assertEquals("用户未登录", result.getMessage());
        }

        @Test
        @DisplayName("403权限不足异常")
        void handleBusinessException_403_returns403() {
            BusinessException ex = new BusinessException(403, "权限不足");
            Result<?> result = handler.handleBusinessException(ex);

            assertEquals(403, result.getCode());
            assertEquals("权限不足", result.getMessage());
        }
    }

    @Nested
    @DisplayName("handleValidException - 参数校验异常")
    class HandleValidException {

        @Test
        @DisplayName("单个字段校验失败")
        void handleValidException_singleError_returns400() {
            // 构造 BindException 来模拟校验失败
            BindException bindEx = new BindException(new Object(), "dto");
            bindEx.addError(new FieldError("dto", "username", "用户名不能为空"));

            ResponseEntity<Result<?>> response = handler.handleBindException(bindEx);

            assertEquals(400, response.getStatusCode().value());
            assertEquals(400, response.getBody().getCode());
            assertEquals("用户名不能为空", response.getBody().getMessage());
        }

        @Test
        @DisplayName("多个字段校验失败，消息用逗号拼接")
        void handleValidException_multipleErrors_joinsMessages() {
            BindException bindEx = new BindException(new Object(), "dto");
            bindEx.addError(new FieldError("dto", "username", "用户名不能为空"));
            bindEx.addError(new FieldError("dto", "password", "密码不能为空"));

            ResponseEntity<Result<?>> response = handler.handleBindException(bindEx);

            assertEquals(400, response.getBody().getCode());
            assertTrue(response.getBody().getMessage().contains("用户名不能为空"));
            assertTrue(response.getBody().getMessage().contains("密码不能为空"));
        }
    }

    @Nested
    @DisplayName("handleMaxUploadSizeException - 文件大小超限")
    class HandleMaxUploadSize {

        @Test
        @DisplayName("返回400和提示信息")
        void handleMaxUploadSize_returns400() {
            MaxUploadSizeExceededException ex = new MaxUploadSizeExceededException(10485760);
            ResponseEntity<Result<?>> response = handler.handleMaxUploadSizeException(ex);

            assertEquals(400, response.getStatusCode().value());
            assertTrue(response.getBody().getMessage().contains("上传文件大小超过限制"));
        }
    }

    @Nested
    @DisplayName("handleException - 未知异常")
    class HandleException {

        @Test
        @DisplayName("未知异常返回500")
        void handleException_unknownException_returns500() {
            Exception ex = new RuntimeException("unexpected error");
            ResponseEntity<Result<?>> response = handler.handleException(ex);

            assertEquals(500, response.getStatusCode().value());
            assertEquals(500, response.getBody().getCode());
            assertEquals("系统内部错误，请联系管理员", response.getBody().getMessage());
        }

        @Test
        @DisplayName("NullPointerException也返回500")
        void handleException_npe_returns500() {
            Exception ex = new NullPointerException();
            ResponseEntity<Result<?>> response = handler.handleException(ex);

            assertEquals(500, response.getStatusCode().value());
        }
    }
}
