package com.agriculture.modules.user.service;

import com.agriculture.modules.user.dto.AdminUpdateUserDTO;
import com.agriculture.modules.user.dto.UpdateUserDTO;
import com.agriculture.modules.user.dto.UserQueryDTO;
import com.agriculture.common.vo.PageResult;
import com.agriculture.modules.user.vo.UserSimpleVO;
import com.agriculture.modules.user.vo.UserVO;

/**
 * 用户服务接口
 */
public interface UserService {

    /**
     * 获取当前用户信息
     */
    UserVO getCurrentUser(String userId);

    /**
     * 更新当前用户信息
     */
    UserVO updateCurrentUser(String userId, UpdateUserDTO dto);

    /**
     * 获取用户列表（管理员）
     */
    PageResult<UserSimpleVO> getUserList(UserQueryDTO dto);

    /**
     * 获取指定用户信息（管理员）
     */
    UserVO getUserById(String id);

    /**
     * 更新用户（管理员）
     */
    UserVO updateUser(String id, AdminUpdateUserDTO dto);

    /**
     * 删除用户（管理员）
     */
    void deleteUser(String id);
}
