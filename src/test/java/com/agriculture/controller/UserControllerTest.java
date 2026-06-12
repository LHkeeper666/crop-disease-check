package com.agriculture.controller;

import com.agriculture.common.exception.BusinessException;
import com.agriculture.common.exception.GlobalExceptionHandler;
import com.agriculture.modules.user.controller.UserController;
import com.agriculture.modules.user.dto.ChangePasswordDTO;
import com.agriculture.modules.user.dto.UpdateStatusDTO;
import com.agriculture.modules.user.service.UserService;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 用户管理控制器测试
 */
@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    private MockMvc mockMvc;

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(userController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new LocalValidatorFactoryBean())
                .build();
    }

    // ==================== 3.3 修改密码接口 ====================

    @Nested
    @DisplayName("3.3 修改密码接口")
    class ChangePassword {

        @Test
        @DisplayName("修改密码成功")
        void changePassword_validRequest_success() throws Exception {
            ChangePasswordDTO dto = new ChangePasswordDTO();
            dto.setOldPassword("OldPass123");
            dto.setNewPassword("NewPass456");
            dto.setConfirmPassword("NewPass456");

            doNothing().when(userService).changePassword(anyString(), any(ChangePasswordDTO.class));

            mockMvc.perform(put("/users/password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto))
                            .requestAttr("userId", "user-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("密码修改成功，请重新登录"));
        }

        @Test
        @DisplayName("原密码不正确时返回40020")
        void changePassword_wrongOldPassword_returns40020() throws Exception {
            ChangePasswordDTO dto = new ChangePasswordDTO();
            dto.setOldPassword("WrongPass");
            dto.setNewPassword("NewPass456");
            dto.setConfirmPassword("NewPass456");

            doThrow(new BusinessException(40020, "原密码不正确"))
                    .when(userService).changePassword(anyString(), any(ChangePasswordDTO.class));

            mockMvc.perform(put("/users/password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto))
                            .requestAttr("userId", "user-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40020))
                    .andExpect(jsonPath("$.message").value("原密码不正确"));
        }

        @Test
        @DisplayName("两次密码不一致时返回40022")
        void changePassword_mismatchConfirm_returns40022() throws Exception {
            ChangePasswordDTO dto = new ChangePasswordDTO();
            dto.setOldPassword("OldPass123");
            dto.setNewPassword("NewPass456");
            dto.setConfirmPassword("DifferentPass");

            doThrow(new BusinessException(40022, "两次输入的密码不一致"))
                    .when(userService).changePassword(anyString(), any(ChangePasswordDTO.class));

            mockMvc.perform(put("/users/password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto))
                            .requestAttr("userId", "user-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40022))
                    .andExpect(jsonPath("$.message").value("两次输入的密码不一致"));
        }

        @Test
        @DisplayName("原密码为空时返回400")
        void changePassword_emptyOldPassword_returns400() throws Exception {
            ChangePasswordDTO dto = new ChangePasswordDTO();
            dto.setOldPassword("");
            dto.setNewPassword("NewPass456");
            dto.setConfirmPassword("NewPass456");

            mockMvc.perform(put("/users/password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto))
                            .requestAttr("userId", "user-001"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("原密码不能为空"));
        }
    }

    // ==================== 3.7 禁用/启用用户接口 ====================

    @Nested
    @DisplayName("3.7 禁用/启用用户接口")
    class UpdateUserStatus {

        @Test
        @DisplayName("禁用用户成功")
        void disableUser_success() throws Exception {
            UpdateStatusDTO dto = new UpdateStatusDTO();
            dto.setStatus("DISABLED");

            doNothing().when(userService).updateUserStatus(anyString(), any(UpdateStatusDTO.class));

            mockMvc.perform(put("/users/user-001/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("用户状态更新成功"));
        }

        @Test
        @DisplayName("启用用户成功")
        void enableUser_success() throws Exception {
            UpdateStatusDTO dto = new UpdateStatusDTO();
            dto.setStatus("ACTIVE");

            doNothing().when(userService).updateUserStatus(anyString(), any(UpdateStatusDTO.class));

            mockMvc.perform(put("/users/user-001/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("用户状态更新成功"));
        }

        @Test
        @DisplayName("状态值不合法时返回400")
        void updateStatus_invalidValue_returns400() throws Exception {
            UpdateStatusDTO dto = new UpdateStatusDTO();
            dto.setStatus("INVALID");

            mockMvc.perform(put("/users/user-001/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("状态值不正确"));
        }

        @Test
        @DisplayName("用户不存在时返回错误")
        void updateStatus_userNotFound_returnsError() throws Exception {
            UpdateStatusDTO dto = new UpdateStatusDTO();
            dto.setStatus("DISABLED");

            doThrow(new BusinessException("用户不存在"))
                    .when(userService).updateUserStatus(anyString(), any(UpdateStatusDTO.class));

            mockMvc.perform(put("/users/non-existent/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.message").value("用户不存在"));
        }
    }

    // ==================== 3.8 重置密码接口 ====================

    @Nested
    @DisplayName("3.8 重置密码接口")
    class ResetPassword {

        @Test
        @DisplayName("重置密码成功")
        void resetPassword_success() throws Exception {
            when(userService.resetPassword("user-001")).thenReturn("Abc123456789");

            mockMvc.perform(post("/users/user-001/reset-password"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.newPassword").value("Abc123456789"))
                    .andExpect(jsonPath("$.message").value("密码重置成功"));
        }

        @Test
        @DisplayName("用户不存在时返回错误")
        void resetPassword_userNotFound_returnsError() throws Exception {
            when(userService.resetPassword("non-existent"))
                    .thenThrow(new BusinessException("用户不存在"));

            mockMvc.perform(post("/users/non-existent/reset-password"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.message").value("用户不存在"));
        }
    }
}
