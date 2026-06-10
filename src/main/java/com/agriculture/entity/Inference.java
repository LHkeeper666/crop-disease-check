package com.agriculture.entity;

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
 * 识别结果表
 * </p>
 *
 * @author agriculture-team
 * @since 2026-06-09
 */
@Getter
@Setter
@TableName("inference")
public class Inference implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 识别UUID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private String id;

    /**
     * 关联上报ID
     */
    @TableField("report_id")
    private String reportId;

    /**
     * 关联病虫害ID
     */
    @TableField("pest_id")
    private String pestId;

    /**
     * 识别的病虫害名称
     */
    @TableField("pest_name")
    private String pestName;

    /**
     * 置信度(0-1)
     */
    @TableField("confidence")
    private BigDecimal confidence;

    /**
     * 是否低置信度(<0.6)
     */
    @TableField("is_low_confidence")
    private Byte isLowConfidence;

    /**
     * 识别管道: DISEASE/PEST
     */
    @TableField("pipeline")
    private String pipeline;

    /**
     * 检测框坐标
     */
    @TableField("bbox")
    private String bbox;

    /**
     * 创建时间
     */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
