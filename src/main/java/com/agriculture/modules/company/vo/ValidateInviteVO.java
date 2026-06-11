package com.agriculture.modules.company.vo;

import lombok.Data;

/**
 * 验证邀请码返回VO
 */
@Data
public class ValidateInviteVO {

    /**
     * 邀请码是否有效
     */
    private Boolean valid;

    /**
     * 企业名称
     */
    private String companyName;
}
