package com.agriculture.modules.pestDiseaseInfo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * 防治方案表
 * </p>
 *
 * @author agriculture-team
 * @since 2026-06-09
 */
@Getter
@Setter
@TableName("prevention_plan")
public class PreventionPlan implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 方案UUID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private String id;

    /**
     * 关联上报ID
     */
    @TableField("report_id")
    private String reportId;

    /**
     * 方案内容
     */
    @TableField("content")
    private String content;

    /**
     * 建议执行时间
     */
    @TableField("suggest_time")
    private LocalDate suggestTime;

    /**
     * 制定人ID
     */
    @TableField("author_id")
    private String authorId;

    /**
     * 版本号
     */
    @TableField("version")
    private Integer version;

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
}
