package com.agriculture.modules.inspection.entity;

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
 * 巡检计划表
 * </p>
 *
 * @author agriculture-team
 * @since 2026-06-09
 */
@Getter
@Setter
@TableName("inspection_plan")
public class InspectionPlan implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 计划UUID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private String id;

    /**
     * 计划名称
     */
    @TableField("name")
    private String name;

    /**
     * Cron表达式
     */
    @TableField("cron_expression")
    private String cronExpression;

    /**
     * 生效开始时间(HH:mm)
     */
    @TableField("active_hours_start")
    private String activeHoursStart;

    /**
     * 生效结束时间(HH:mm)
     */
    @TableField("active_hours_end")
    private String activeHoursEnd;

    /**
     * 是否启用
     */
    @TableField("is_active")
    private Byte isActive;

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
