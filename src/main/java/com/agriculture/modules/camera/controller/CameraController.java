package com.agriculture.modules.camera.controller;

import com.agriculture.modules.camera.dto.CameraDetectRequest;
import com.agriculture.modules.camera.dto.CameraDetectResponse;
import com.agriculture.modules.camera.service.CameraDetectService;
import com.agriculture.common.vo.Result;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 摄像头表 前端控制器
 * </p>
 *
 * @author agriculture-team
 * @since 2026-06-09
 */
@RestController
@RequestMapping("/camera")
public class CameraController {

    private final CameraDetectService cameraDetectService;

    public CameraController(CameraDetectService cameraDetectService) {
        this.cameraDetectService = cameraDetectService;
    }

    /**
     * 摄像头实时识别
     * 对指定摄像头的实时视频流进行抽帧并执行病虫害识别
     *
     * @param cameraId 摄像头ID
     * @param request  识别请求参数
     * @return 识别结果
     */
    @PostMapping("/{cameraId}/detect")
    public Result<CameraDetectResponse> detect(
            @PathVariable String cameraId,
            @RequestBody(required = false) CameraDetectRequest request) {
        if (request == null) {
            request = new CameraDetectRequest();
        }
        CameraDetectResponse response = cameraDetectService.detect(cameraId, request);
        return Result.success(response);
    }
}
