package com.agriculture.modules.company.vo;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 企业成员返回VO
 */
@Data
public class CompanyMemberVO {

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
     * 是否已通过审批
     */
    private Boolean approved;

    /**
     * 加入时间
     */
    private LocalDateTime joinedAt;
}
