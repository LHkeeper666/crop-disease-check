package com.agriculture.modules.inspection.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * 巡检日志表
 * </p>
 *
 * @author agriculture-team
 * @since 2026-06-09
 */
@Getter
@Setter
@TableName("inspection_log")
public class InspectionLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 日志UUID
     */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /**
     * 计划ID
     */
    @TableField("plan_id")
    private String planId;

    /**
     * 摄像头ID
     */
    @TableField("camera_id")
    private String cameraId;

    /**
     * 抓拍时间
     */
    @TableField("capture_time")
    private LocalDateTime captureTime;

    /**
     * 图片URL
     */
    @TableField("image_url")
    private String imageUrl;

    /**
     * 病害数量
     */
    @TableField("disease_count")
    private Integer diseaseCount;

    /**
     * 虫害数量
     */
    @TableField("pest_count")
    private Integer pestCount;

    /**
     * 最高置信度
     */
    @TableField("max_confidence")
    private BigDecimal maxConfidence;

    /**
     * 耗时(毫秒)
     */
    @TableField("duration_ms")
    private Integer durationMs;

    /**
     * 状态: SUCCESS/FAILED
     */
    @TableField("status")
    private String status;

    /**
     * 创建时间
     */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
