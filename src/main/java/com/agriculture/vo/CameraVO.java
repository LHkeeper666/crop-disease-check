package com.agriculture.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class CameraVO {

    private String id;
    private String name;
    private String rtspUrl;
    private String rtspUrlSub;
    private BigDecimal locationX;
    private BigDecimal locationY;
    private BigDecimal direction;
    private List<String> coverageGrids;
    private String captureResolution;
    private Integer captureQuality;
    private Integer reconnectInterval;
    private String status;
    private LocalDateTime lastOnlineAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
