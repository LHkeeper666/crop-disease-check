package com.agriculture.modules.camera.dto;

import lombok.Data;

/**
 * 摄像头检测请求DTO
 */
@Data
public class CameraDetectRequest {

    /**
     * 置信度阈值，默认0.5，范围0.1-1.0
     */
    private Float confidence = 0.5f;

    /**
     * 是否使用子码流（低分辨率），默认false
     */
    private Boolean useSubStream = false;

    /**
     * 是否保存抓拍图片到服务器，默认false（定时监测时不保存，减少IO）
     */
    private Boolean saveCapture = false;
}
