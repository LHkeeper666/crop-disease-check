package com.agriculture.modules.report.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 图像上报上传响应VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportUploadVO {

    /**
     * 上报记录ID
     */
    private String reportId;

    /**
     * 图片URL列表
     */
    private List<String> imageUrls;

    /**
     * 状态
     */
    private String status;
}
