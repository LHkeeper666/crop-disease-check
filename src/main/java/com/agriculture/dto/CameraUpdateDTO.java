package com.agriculture.dto;

import lombok.Data;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

@Data
public class CameraUpdateDTO {

    @Size(max = 128, message = "摄像头名称不能超过128个字符")
    private String name;

    @Pattern(regexp = "^rtsp://.*", message = "RTSP地址必须以rtsp://开头")
    private String rtspUrl;

    private String rtspUrlSub;

    private BigDecimal locationX;

    private BigDecimal locationY;

    private BigDecimal direction;

    private String captureResolution;

    @Min(value = 1, message = "抓拍JPEG质量最小为1")
    @Max(value = 100, message = "抓拍JPEG质量最大为100")
    private Integer captureQuality;

    @Min(value = 10, message = "断流重连间隔最小为10秒")
    @Max(value = 300, message = "断流重连间隔最大为300秒")
    private Integer reconnectInterval;

    private List<String> coverageGrids;
}
