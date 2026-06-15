package com.agriculture.modules.camera.controller;

import com.agriculture.modules.camera.dto.*;
import com.agriculture.modules.camera.entity.Camera;
import com.agriculture.modules.camera.service.CameraDetectService;
import com.agriculture.modules.camera.service.CameraService;
import com.agriculture.common.annotation.RequireRole;
import com.agriculture.common.vo.Result;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;


import java.util.Map;

/**
 * 摄像头管理控制器
 */
@RestController
@RequestMapping("/camera")
public class CameraController {

    private final CameraService cameraService;
    private final CameraDetectService cameraDetectService;

    public CameraController(CameraService cameraService,
                            CameraDetectService cameraDetectService) {
        this.cameraService = cameraService;
        this.cameraDetectService = cameraDetectService;
    }

    // ==================== CRUD ====================

    /**
     * 5.1 摄像头列表
     */
    @GetMapping("/list")
    public Result<Page<Camera>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(cameraService.listCameras(status, keyword, page, size));
    }

    /**
     * 5.2 新增摄像头
     */
    @PostMapping
    @RequireRole({"ADMIN"})
    public Result<Map<String, String>> add(@Valid @RequestBody CameraCreateRequest request) {
        String id = cameraService.createCamera(request);
        return Result.success("摄像头添加成功，正在尝试建立RTSP连接", Map.of("id", id));
    }

    /**
     * 5.3 修改摄像头
     */
    @PutMapping("/{id}")
    @RequireRole({"ADMIN"})
    public Result<Void> update(@PathVariable String id,
                               @Valid @RequestBody CameraUpdateRequest request) {
        cameraService.updateCamera(id, request);
        return Result.success("摄像头更新成功", null);
    }

    /**
     * 5.4 删除摄像头
     */
    @DeleteMapping("/{id}")
    @RequireRole({"ADMIN"})
    public Result<Void> delete(@PathVariable String id) {
        cameraService.deleteCamera(id);
        return Result.success("摄像头删除成功，已断开RTSP连接", null);
    }

    // ==================== 状态与流 ====================

    /**
     * 手动重连RTSP
     */
    @PostMapping("/{id}/reconnect")
    @RequireRole({"ADMIN"})
    public Result<Void> reconnect(@PathVariable String id) {
        cameraService.reconnect(id);
        return Result.success("正在尝试重新连接RTSP流", null);
    }

    /**
     * 5.5 获取摄像头实时状态
     */
    @GetMapping("/{id}/status")
    public Result<CameraStatusVO> status(@PathVariable String id) {
        return Result.success(cameraService.getCameraStatus(id));
    }

    /**
     * 5.7 批量状态查询
     */
    @PostMapping("/batch-status")
    @RequireRole({"ADMIN", "MANAGER"})
    public Result<CameraBatchStatusVO> batchStatus(@RequestBody CameraBatchStatusRequest request) {
        return Result.success(cameraService.batchStatus(request.getCameraIds()));
    }

    // ==================== 推理 ====================

    /**
     * 摄像头实时识别（对单个摄像头抽帧推理）
     */
    @PostMapping("/{cameraId}/detect")
    public Result<CameraDetectResponse> detect(
            @PathVariable String cameraId,
            @RequestBody(required = false) CameraDetectRequest request) {
        if (request == null) {
            request = new CameraDetectRequest();
        }
        return Result.success(cameraDetectService.detect(cameraId, request));
    }

    /**
     * 5.6 更新抓拍配置
     */
    @PutMapping("/{id}/capture-config")
    @RequireRole({"ADMIN"})
    public Result<Void> updateCaptureConfig(@PathVariable String id,
                                            @RequestBody CameraCaptureConfigRequest request) {
        cameraService.updateCaptureConfig(id, request);
        return Result.success("抓拍配置更新成功", null);
    }

    /**
     * 启动/停止摄像头实时监测模式
     * 启动后将按指定间隔持续抽帧推理，结果通过 WebSocket 推送
     */
    @PostMapping("/{id}/monitor")
    @RequireRole({"ADMIN", "MANAGER"})
    public Result<Void> toggleMonitor(@PathVariable String id,
                                      @RequestBody CameraMonitorRequest request) {
        cameraDetectService.toggleMonitor(id, request);
        return Result.success(request.getEnabled() ? "实时监测已启动" : "实时监测已停止", null);
    }
}
