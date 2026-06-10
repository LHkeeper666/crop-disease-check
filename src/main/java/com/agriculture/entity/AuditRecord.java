package com.agriculture.entity;

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
 * 审核记录表
 * </p>
 *
 * @author agriculture-team
 * @since 2026-06-09
 */
@Getter
@Setter
@TableName("audit_record")
public class AuditRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 审核UUID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private String id;

    /**
     * 关联上报ID
     */
    @TableField("report_id")
    private String reportId;

    /**
     * 审核人ID
     */
    @TableField("auditor_id")
    private String auditorId;

    /**
     * 审核结果: APPROVED/REJECTED
     */
    @TableField("audit_result")
    private String auditResult;

    /**
     * 审核意见
     */
    @TableField("comment")
    private String comment;

    /**
     * 审核时间
     */
    @TableField("audited_at")
    private LocalDateTime auditedAt;
}
