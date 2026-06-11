package com.agriculture.vo;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 企业信息返回VO
 */
@Data
public class CompanyInfoVO {

    /**
     * 企业UUID
     */
    private String id;

    /**
     * 企业名称
     */
    private String name;

    /**
     * 邀请码
     */
    private String inviteCode;

    /**
     * 成员数量
     */
    private Integer memberCount;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
