package com.agriculture.modules.company.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 企业表
 */
@Getter
@Setter
@TableName("company")
public class Company implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 企业UUID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private String id;

    /**
     * 企业名称
     */
    @TableField("name")
    private String name;

    /**
     * 邀请码
     */
    @TableField("invite_code")
    private String inviteCode;

    /**
     * 邀请码过期时间
     */
    @TableField("expire_at")
    private LocalDateTime expireAt;

    /**
     * 成员上限
     */
    @TableField("member_limit")
    private Integer memberLimit;

    /**
     * 创建时间
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    /**
     * 逻辑删除
     */
    @TableField("deleted")
    private Byte deleted;
}
