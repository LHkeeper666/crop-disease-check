package com.agriculture.modules.camera.service;

import com.agriculture.modules.camera.dto.CameraDetectRequest;
import com.agriculture.modules.camera.dto.CameraDetectResponse;

/**
 * 摄像头实时识别服务接口
 */
public interface CameraDetectService {

    /**
     * 对指定摄像头进行实时抽帧识别
     *
     * @param cameraId 摄像头ID
     * @param request  识别请求参数
     * @return 识别结果
     */
    CameraDetectResponse detect(String cameraId, CameraDetectRequest request);
}
