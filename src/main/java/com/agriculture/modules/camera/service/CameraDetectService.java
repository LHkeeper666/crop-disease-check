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
     * 启动/停止摄像头实时监测模式
     */
    void toggleMonitor(String cameraId, CameraMonitorRequest request);

    /**
     * 从摄像头 RTSP 流抓取单帧图像（不执行推理）
     *
     * @param cameraId 摄像头 ID
     * @return JPEG 图像字节数组
     */
    byte[] captureSnapshot(String cameraId);
}
