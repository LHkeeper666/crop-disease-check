package com.agriculture.modules.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * 图像上报记录表
 * </p>
 *
 * @author agriculture-team
 * @since 2026-06-09
 */
@Getter
@Setter
@TableName("report")
public class Report implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 上报UUID
     */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /**
     * 上报用户ID
     */
    @TableField("user_id")
    private String userId;

    /**
     * 网格/地块ID
     */
    @TableField("grid_id")
    private String gridId;

    /**
     * 农作物品种
     */
    @TableField("crop_type")
    private String cropType;

    /**
     * 图片URL数组
     */
    @TableField("image_urls")
    private String imageUrls;

    /**
     * 发现时间
     */
    @TableField("found_at")
    private LocalDateTime foundAt;

    /**
     * 补充描述
     */
    @TableField("description")
    private String description;

    /**
     * 状态: PENDING_RECOGNITION/PENDING/AUDITED/REJECTED
     */
    @TableField("status")
    private String status;

    /**
     * 所属企业ID
     */
    @TableField("company_id")
    private String companyId;

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
