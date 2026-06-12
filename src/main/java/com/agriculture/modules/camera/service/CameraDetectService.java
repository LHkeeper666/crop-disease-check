package com.agriculture.modules.camera.service;

import com.agriculture.modules.camera.dto.*;

/**
 * 摄像头检测服务接口
 */
public interface CameraDetectService {

    /**
     * 对指定摄像头进行实时抽帧识别
     */
    CameraDetectResponse detect(String cameraId, CameraDetectRequest request);

    /**
     * 手动抓拍
     */
    CameraCaptureVO capture(String cameraId, CameraCaptureRequest request);

    /**
     * 批量抓拍
     */
    CameraBatchCaptureVO batchCapture(CameraBatchCaptureRequest request);

    /**
     * 启动/停止摄像头实时监测模式
     */
    void toggleMonitor(String cameraId, CameraMonitorRequest request);
}
