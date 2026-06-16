package com.agriculture.modules.camera.service;

import com.agriculture.modules.camera.dto.*;
import com.agriculture.modules.camera.entity.Camera;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 摄像头服务接口
 */
public interface CameraService extends IService<Camera> {

    /**
     * 摄像头列表（带筛选和分页）
     */
    Page<Camera> listCameras(String status, String keyword, int page, int size, String companyId);

    /**
     * 新增摄像头
     */
    String createCamera(CameraCreateRequest request, String companyId);

    /**
     * 修改摄像头
     */
    void updateCamera(String id, CameraUpdateRequest request);

    /**
     * 删除摄像头
     */
    void deleteCamera(String id);

    /**
     * 获取摄像头实时状态
     */
    CameraStatusVO getCameraStatus(String id);

    /**
     * 批量获取摄像头状态
     */
    CameraBatchStatusVO batchStatus(java.util.List<String> cameraIds);

    /**
     * 更新抓拍配置
     */
    void updateCaptureConfig(String id, CameraCaptureConfigRequest request);

    /**
     * 手动重连摄像头RTSP
     */
    void reconnect(String cameraId);
}
