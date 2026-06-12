package com.agriculture.modules.report.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * 图像上报上传请求DTO（非文件部分）
 */
@Data
public class ReportUploadDTO {

    /**
     * 地块/网格ID
     */
    @NotBlank(message = "地块编号不能为空")
    private String gridId;

    /**
     * 农作物品种
     */
    @NotBlank(message = "农作物品种不能为空")
    private String cropType;

    /**
     * 发现时间
     */
    @NotNull(message = "发现时间不能为空")
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime foundAt;

    /**
     * 补充描述
     */
    private String description;
}
