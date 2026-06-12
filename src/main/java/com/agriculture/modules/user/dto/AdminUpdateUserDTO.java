package com.agriculture.modules.user.dto;

import lombok.Data;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 管理员更新用户信息请求DTO
 */
@Data
public class AdminUpdateUserDTO {

    /**
     * 真实姓名
     */
    @Size(max = 64, message = "姓名长度不能超过64个字符")
    private String name;

    /**
     * 手机号
     */
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /**
     * 角色: ADMIN/EXPERT/MANAGER/VISITOR
     */
    @Pattern(regexp = "^(ADMIN|EXPERT|MANAGER|VISITOR)$", message = "角色值不正确")
    private String role;

    /**
     * 状态: ACTIVE/DISABLED
     */
    @Pattern(regexp = "^(ACTIVE|DISABLED)$", message = "状态值不正确")
    private String status;
}
