package com.agriculture.dto;

import lombok.Data;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Data
public class CaptureConfigDTO {

    private String captureResolution;

    @Min(value = 1, message = "抓拍JPEG质量最小为1")
    @Max(value = 100, message = "抓拍JPEG质量最大为100")
    private Integer captureQuality;

    @Min(value = 10, message = "断流重连间隔最小为10秒")
    @Max(value = 300, message = "断流重连间隔最大为300秒")
    private Integer reconnectInterval;
}
