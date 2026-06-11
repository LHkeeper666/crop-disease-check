package com.agriculture.controller;

import com.agriculture.annotation.RequireRole;
import com.agriculture.dto.AdminUpdateUserDTO;
import com.agriculture.dto.UpdateUserDTO;
import com.agriculture.dto.UserQueryDTO;
import com.agriculture.service.UserService;
import com.agriculture.vo.PageResult;
import com.agriculture.vo.Result;
import com.agriculture.vo.UserSimpleVO;
import com.agriculture.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * 用户管理控制器
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 获取当前用户信息（登录用户可访问）
     */
    @GetMapping("/me")
    public Result<UserVO> getCurrentUser(HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        UserVO user = userService.getCurrentUser(userId);
        return Result.success(user);
    }

    /**
     * 更新当前用户信息（登录用户可访问）
     */
    @PutMapping("/me")
    public Result<UserVO> updateCurrentUser(
            HttpServletRequest request,
            @Valid @RequestBody UpdateUserDTO dto) {
        String userId = (String) request.getAttribute("userId");
        UserVO user = userService.updateCurrentUser(userId, dto);
        return Result.success("更新成功", user);
    }

    /**
     * 获取用户列表（仅管理员）
     */
    @RequireRole("ADMIN")
    @GetMapping
    public Result<PageResult<UserSimpleVO>> getUserList(UserQueryDTO dto) {
        PageResult<UserSimpleVO> result = userService.getUserList(dto);
        return Result.success(result);
    }

    /**
     * 获取指定用户信息（仅管理员）
     */
    @RequireRole("ADMIN")
    @GetMapping("/{id}")
    public Result<UserVO> getUserById(@PathVariable String id) {
        UserVO user = userService.getUserById(id);
        return Result.success(user);
    }

    /**
     * 更新用户（仅管理员）
     */
    @RequireRole("ADMIN")
    @PutMapping("/{id}")
    public Result<UserVO> updateUser(
            @PathVariable String id,
            @Valid @RequestBody AdminUpdateUserDTO dto) {
        UserVO user = userService.updateUser(id, dto);
        return Result.success("更新成功", user);
    }

    /**
     * 删除用户（仅管理员）
     */
    @RequireRole("ADMIN")
    @DeleteMapping("/{id}")
    public Result<Void> deleteUser(@PathVariable String id) {
        userService.deleteUser(id);
        return Result.success("删除成功", null);
    }
}
