package com.agriculture.modules.workorder.entity;

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
 * 工单表
 * </p>
 *
 * @author agriculture-team
 * @since 2026-06-09
 */
@Getter
@Setter
@TableName("work_order")
public class WorkOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 工单UUID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private String id;

    /**
     * 工单标题
     */
    @TableField("title")
    private String title;

    /**
     * 严重程度: LOW/MEDIUM/HIGH/CRITICAL
     */
    @TableField("severity")
    private String severity;

    /**
     * 状态: PENDING/PROCESSING/DONE/IGNORED/ESCALATED
     */
    @TableField("status")
    private String status;

    /**
     * 关联识别ID
     */
    @TableField("inference_id")
    private String inferenceId;

    /**
     * 指派给用户ID
     */
    @TableField("assigned_to")
    private String assignedTo;

    /**
     * 专家备注
     */
    @TableField("expert_comment")
    private String expertComment;

    /**
     * 回调Token
     */
    @TableField("callback_token")
    private String callbackToken;

    /**
     * Token过期时间
     */
    @TableField("token_expire_at")
    private LocalDateTime tokenExpireAt;

    /**
     * Token是否已使用
     */
    @TableField("token_used")
    private Byte tokenUsed;

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
     * 工单类型: disease/pest
     */
    @TableField("type")
    private String type;

    /**
     * 关联网格编号
     */
    @TableField("grid_label")
    private String gridLabel;

    /**
     * 病虫害名称
     */
    @TableField("pest_name")
    private String pestName;

    /**
     * 检测置信度
     */
    @TableField("confidence")
    private BigDecimal confidence;

    /**
     * 创建人ID
     */
    @TableField("created_by")
    private String createdBy;

    /**
     * 所属企业ID
     */
    @TableField("company_id")
    private String companyId;
}
