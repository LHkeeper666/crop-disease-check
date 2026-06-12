package com.agriculture.modules.user.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户信息返回VO
 */
@Data
public class UserVO {

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
     * 用户头像路径
     */
    private String avatar;

    /**
     * 所属企业ID
     */
    private String companyId;

    /**
     * 是否已通过审批加入企业
     */
    private Boolean approved;

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

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
