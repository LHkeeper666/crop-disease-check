package com.agriculture.modules.user.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户简要信息返回VO（用于列表）
 */
@Data
public class UserSimpleVO {

    /**
     * 用户UUID
     */
    private String id;

    /**
     * 用户名
     */
    private String username;

    /**
     * 真实姓名
     */
    private String name;

    /**
     * 角色
     */
    private String role;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 状态
     */
    private String status;

    /**
     * 最后登录时间
     */
    private LocalDateTime lastLoginAt;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
