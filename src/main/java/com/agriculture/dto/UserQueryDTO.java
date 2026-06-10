package com.agriculture.dto;

import lombok.Data;

/**
 * 用户查询请求DTO
 */
@Data
public class UserQueryDTO {

    /**
     * 页码
     */
    private Integer page = 1;

    /**
     * 每页大小
     */
    private Integer size = 10;

    /**
     * 搜索关键词（用户名、姓名、手机号）
     */
    private String keyword;

    /**
     * 角色
     */
    private String role;

    /**
     * 状态
     */
    private String status;
}
