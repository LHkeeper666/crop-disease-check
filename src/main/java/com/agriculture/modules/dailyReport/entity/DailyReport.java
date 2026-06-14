package com.agriculture.modules.dailyReport.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * 日度报告表
 * </p>
 *
 * @author agriculture-team
 * @since 2026-06-09
 */
@Getter
@Setter
@TableName("daily_report")
public class DailyReport implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 报告UUID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private String id;

    /**
     * 报告日期
     */
    @TableField("report_date")
    private LocalDate reportDate;

    /**
     * 识别总次数
     */
    @TableField("detections")
    private Integer detections;

    /**
     * 病害次数
     */
    @TableField("disease_count")
    private Integer diseaseCount;

    /**
     * 虫害次数
     */
    @TableField("pest_count")
    private Integer pestCount;

    /**
     * 处理率(0.00-1.00)
     */
    @TableField("handled_rate")
    private BigDecimal handledRate;

    /**
     * 关联温室ID
     */
    @TableField("greenhouse_id")
    private String greenhouseId;

    /**
     * 统计数据JSON
     */
    @TableField("summary_json")
    private String summaryJson;

    /**
     * 报告HTML内容
     */
    @TableField("html_content")
    private String htmlContent;

    /**
     * 是否已发送邮件
     */
    @TableField("email_sent")
    private Byte emailSent;

    /**
     * 邮件发送时间
     */
    @TableField("email_sent_at")
    private LocalDateTime emailSentAt;

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
}
