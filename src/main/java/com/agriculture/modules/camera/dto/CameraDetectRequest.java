package com.agriculture.modules.camera.dto;

import lombok.Data;

/**
 * 摄像头实时识别请求DTO
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
     * 是否返回标注图base64，默认true
     */
    private Boolean returnAnnotatedImage = true;

    /**
     * 是否保存抓拍图片到服务器，默认true
     */
    private Boolean saveCapture = true;
}
