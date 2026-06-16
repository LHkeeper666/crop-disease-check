package com.agriculture.service;

import cn.hutool.crypto.digest.BCrypt;
import com.agriculture.common.exception.BusinessException;
import com.agriculture.common.vo.PageResult;
import com.agriculture.modules.user.dto.AdminUpdateUserDTO;
import com.agriculture.modules.user.dto.ChangePasswordDTO;
import com.agriculture.modules.user.dto.UpdateStatusDTO;
import com.agriculture.modules.user.dto.UpdateUserDTO;
import com.agriculture.modules.user.dto.UserQueryDTO;
import com.agriculture.modules.user.entity.SysUser;
import com.agriculture.modules.user.mapper.SysUserMapper;
import com.agriculture.modules.user.service.impl.UserServiceImpl;
import com.agriculture.modules.user.vo.UserSimpleVO;
import com.agriculture.modules.user.vo.UserVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("UserServiceImpl 单元测试")
class UserServiceImplTest {

    @Mock
    private SysUserMapper userMapper;

    @InjectMocks
    private UserServiceImpl userService;

    private SysUser sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = new SysUser();
        sampleUser.setId("u-001");
        sampleUser.setUsername("admin");
        sampleUser.setPassword(BCrypt.hashpw("admin123"));
        sampleUser.setName("系统管理员");
        sampleUser.setPhone("13800138000");
        sampleUser.setEmail("admin@agriculture.com");
        sampleUser.setRole("ADMIN");
        sampleUser.setStatus("ACTIVE");
        sampleUser.setCompanyId("company-001");
        sampleUser.setDeleted((byte) 0);
        sampleUser.setCreatedAt(LocalDateTime.now());
        sampleUser.setUpdatedAt(LocalDateTime.now());
    }

    // ==================== getCurrentUser ====================

    @Nested
    @DisplayName("getCurrentUser - 获取当前用户")
    class GetCurrentUser {

        @Test
        @DisplayName("用户存在返回UserVO")
        void getCurrentUser_exists_returnsVO() {
            when(userMapper.selectById("u-001")).thenReturn(sampleUser);

            UserVO result = userService.getCurrentUser("u-001");

            assertEquals("u-001", result.getId());
            assertEquals("admin", result.getUsername());
            assertEquals("系统管理员", result.getName());
        }

        @Test
        @DisplayName("用户不存在抛异常")
        void getCurrentUser_notFound_throwsException() {
            when(userMapper.selectById("not-exist")).thenReturn(null);

            assertThrows(BusinessException.class,
                    () -> userService.getCurrentUser("not-exist"));
        }
    }

    // ==================== updateCurrentUser ====================

    @Nested
    @DisplayName("updateCurrentUser - 更新当前用户")
    class UpdateCurrentUser {

        @Test
        @DisplayName("更新成功返回新信息")
        void updateCurrentUser_valid_returnsUpdatedVO() {
            when(userMapper.selectById("u-001")).thenReturn(sampleUser);
            when(userMapper.updateById(any(SysUser.class))).thenReturn(1);

            UpdateUserDTO dto = new UpdateUserDTO();
            dto.setName("新名字");
            dto.setPhone("13900139000");

            UserVO result = userService.updateCurrentUser("u-001", dto);

            assertEquals("新名字", result.getName());
            assertEquals("13900139000", result.getPhone());
            verify(userMapper).updateById(any(SysUser.class));
        }

        @Test
        @DisplayName("用户不存在抛异常")
        void updateCurrentUser_notFound_throwsException() {
            when(userMapper.selectById("not-exist")).thenReturn(null);

            UpdateUserDTO dto = new UpdateUserDTO();
            dto.setName("新名字");

            assertThrows(BusinessException.class,
                    () -> userService.updateCurrentUser("not-exist", dto));
        }
    }

    // ==================== getUserList ====================

    @Nested
    @DisplayName("getUserList - 用户列表")
    class GetUserList {

        @Test
        @DisplayName("无条件查询返回分页数据")
        void getUserList_noFilters_returnsPage() {
            Page<SysUser> page = new Page<>(1, 20, 1);
            page.setRecords(List.of(sampleUser));
            when(userMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

            UserQueryDTO dto = new UserQueryDTO();
            dto.setPage(1);
            dto.setSize(20);

            PageResult<UserSimpleVO> result = userService.getUserList(dto);

            assertEquals(1, result.getTotal());
            assertEquals("admin", result.getRecords().get(0).getUsername());
        }
    }

    // ==================== changePassword ====================

    @Nested
    @DisplayName("changePassword - 修改密码")
    class ChangePassword {

        @Test
        @DisplayName("修改成功")
        void changePassword_correctOldPassword_success() {
            when(userMapper.selectById("u-001")).thenReturn(sampleUser);
            when(userMapper.updateById(any(SysUser.class))).thenReturn(1);

            ChangePasswordDTO dto = new ChangePasswordDTO();
            dto.setOldPassword("admin123");
            dto.setNewPassword("newPass123");
            dto.setConfirmPassword("newPass123");

            userService.changePassword("u-001", dto);

            verify(userMapper).updateById(any(SysUser.class));
            assertTrue(BCrypt.checkpw("newPass123", sampleUser.getPassword()));
        }

        @Test
        @DisplayName("旧密码错误抛异常")
        void changePassword_wrongOldPassword_throwsException() {
            when(userMapper.selectById("u-001")).thenReturn(sampleUser);

            ChangePasswordDTO dto = new ChangePasswordDTO();
            dto.setOldPassword("wrong-password");
            dto.setNewPassword("newPass123");
            dto.setConfirmPassword("newPass123");

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> userService.changePassword("u-001", dto));
            assertEquals(40020, ex.getCode());
        }

        @Test
        @DisplayName("两次密码不一致抛异常")
        void changePassword_mismatchedConfirm_throwsException() {
            when(userMapper.selectById("u-001")).thenReturn(sampleUser);

            ChangePasswordDTO dto = new ChangePasswordDTO();
            dto.setOldPassword("admin123");
            dto.setNewPassword("newPass123");
            dto.setConfirmPassword("differentPass");

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> userService.changePassword("u-001", dto));
            assertEquals(40022, ex.getCode());
        }

        @Test
        @DisplayName("新密码与原密码相同抛异常")
        void changePassword_sameAsOld_throwsException() {
            when(userMapper.selectById("u-001")).thenReturn(sampleUser);

            ChangePasswordDTO dto = new ChangePasswordDTO();
            dto.setOldPassword("admin123");
            dto.setNewPassword("admin123");
            dto.setConfirmPassword("admin123");

            assertThrows(BusinessException.class,
                    () -> userService.changePassword("u-001", dto));
        }
    }

    // ==================== updateUserStatus ====================

    @Nested
    @DisplayName("updateUserStatus - 更新用户状态")
    class UpdateUserStatus {

        @Test
        @DisplayName("禁用用户成功")
        void updateUserStatus_disable_success() {
            when(userMapper.selectById("u-001")).thenReturn(sampleUser);
            when(userMapper.updateById(any(SysUser.class))).thenReturn(1);

            UpdateStatusDTO dto = new UpdateStatusDTO();
            dto.setStatus("DISABLED");

            userService.updateUserStatus("u-001", dto);

            assertEquals("DISABLED", sampleUser.getStatus());
        }

        @Test
        @DisplayName("用户不存在抛异常")
        void updateUserStatus_notFound_throwsException() {
            when(userMapper.selectById("not-exist")).thenReturn(null);

            UpdateStatusDTO dto = new UpdateStatusDTO();
            dto.setStatus("DISABLED");

            assertThrows(BusinessException.class,
                    () -> userService.updateUserStatus("not-exist", dto));
        }
    }

    // ==================== resetPassword ====================

    @Nested
    @DisplayName("resetPassword - 重置密码")
    class ResetPassword {

        @Test
        @DisplayName("重置成功")
        void resetPassword_existingUser_success() {
            when(userMapper.selectById("u-001")).thenReturn(sampleUser);
            when(userMapper.updateById(any(SysUser.class))).thenReturn(1);

            userService.resetPassword("u-001", "newPassword123");

            verify(userMapper).updateById(any(SysUser.class));
            assertTrue(BCrypt.checkpw("newPassword123", sampleUser.getPassword()));
        }

        @Test
        @DisplayName("用户不存在抛异常")
        void resetPassword_notFound_throwsException() {
            when(userMapper.selectById("not-exist")).thenReturn(null);

            assertThrows(BusinessException.class,
                    () -> userService.resetPassword("not-exist", "newPass"));
        }
    }

    // ==================== deleteUser ====================

    @Nested
    @DisplayName("deleteUser - 删除用户")
    class DeleteUser {

        @Test
        @DisplayName("逻辑删除成功")
        void deleteUser_existing_success() {
            when(userMapper.selectById("u-001")).thenReturn(sampleUser);
            when(userMapper.updateById(any(SysUser.class))).thenReturn(1);

            userService.deleteUser("u-001");

            assertEquals((byte) 1, sampleUser.getDeleted());
            verify(userMapper).updateById(sampleUser);
        }

        @Test
        @DisplayName("用户不存在抛异常")
        void deleteUser_notFound_throwsException() {
            when(userMapper.selectById("not-exist")).thenReturn(null);

            assertThrows(BusinessException.class,
                    () -> userService.deleteUser("not-exist"));
        }
    }
}
