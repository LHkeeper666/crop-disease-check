package com.agriculture.modules.camera.service.impl;

import com.agriculture.modules.camera.dto.*;
import com.agriculture.modules.camera.entity.Camera;
import com.agriculture.modules.camera.mapper.CameraMapper;
import com.agriculture.modules.camera.service.CameraDetectService;
import com.agriculture.common.exception.BusinessException;
import com.agriculture.modules.inference.service.InferenceClient;
import com.agriculture.common.websocket.WebSocketService;
import com.fasterxml.jackson.databind.JsonNode;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/**
 * 摄像头检测服务实现
 *
 * 架构说明：
 * - 后端负责：RTSP抽帧 → 调用推理服务 → 推送检测框坐标(bbox)到WebSocket
 * - 前端负责：播放HLS视频流 + Canvas叠加层绘制检测框
 * - 推理服务不返回标注图(base64)，只返回结构化的检测数据
 */
@Service
public class CameraDetectServiceImpl implements CameraDetectService {

    private static final Logger log = LoggerFactory.getLogger(CameraDetectServiceImpl.class);

    private final CameraMapper cameraMapper;
    private final InferenceClient inferenceClient;
    private final WebSocketService webSocketService;

    @Value("${capture.save-path:./captures}")
    private String captureSavePath;

    @Value("${capture.timeout-ms:10000}")
    private int captureTimeoutMs;

    @Value("${capture.transport:tcp}")
    private String captureTransport;

    /**
     * 抽帧结果：图片字节 + 实际宽高
     */
    private static class CapturedFrame {
        final byte[] bytes;
        final int width;
        final int height;
        CapturedFrame(byte[] bytes, int width, int height) {
            this.bytes = bytes;
            this.width = width;
            this.height = height;
        }
    }

    /**
     * 实时监测任务调度器
     */
    private final ScheduledExecutorService monitorScheduler = Executors.newScheduledThreadPool(4);

    /**
     * 活跃的监测任务 Map<cameraId, ScheduledFuture>
     */
    private final Map<String, ScheduledFuture<?>> activeMonitors = new ConcurrentHashMap<>();

    public CameraDetectServiceImpl(CameraMapper cameraMapper,
                                   InferenceClient inferenceClient,
                                   WebSocketService webSocketService) {
        this.cameraMapper = cameraMapper;
        this.inferenceClient = inferenceClient;
        this.webSocketService = webSocketService;
    }

    @Override
    public CameraDetectResponse detect(String cameraId, CameraDetectRequest request) {
        Camera camera = cameraMapper.selectById(cameraId);
        if (camera == null) {
            throw new BusinessException(40087, "摄像头不存在");
        }
        if (!"ONLINE".equals(camera.getStatus())) {
            throw new BusinessException(40082, "摄像头离线，无法抓拍");
        }

        String rtspUrl = Boolean.TRUE.equals(request.getUseSubStream()) && camera.getRtspUrlSub() != null
                ? camera.getRtspUrlSub()
                : camera.getRtspUrl();

        if (rtspUrl == null || rtspUrl.isEmpty()) {
            throw new BusinessException(40084, "摄像头RTSP地址未配置");
        }

        // 1. 从RTSP流抽帧（使用源流分辨率，不强制缩放）
        CapturedFrame captured;
        try {
            captured = captureFrameFromRtsp(rtspUrl);
        } catch (Exception e) {
            log.error("RTSP抽帧失败: cameraId={}, rtspUrl={}", cameraId, rtspUrl, e);
            camera.setStatus("OFFLINE");
            cameraMapper.updateById(camera);
            throw new BusinessException(40084, "抓拍失败: " + e.getMessage());
        }

        // 2. 保存抓拍图片（可选）
        String captureUrl = null;
        if (Boolean.TRUE.equals(request.getSaveCapture())) {
            captureUrl = saveCaptureImage(cameraId, captured.bytes);
        }

        // 3. 调用推理服务（不返回标注图，只返回bbox坐标）
        float confidence = request.getConfidence() != null ? request.getConfidence() : 0.5f;

        JsonNode inferenceResult;
        try {
            String base64Image = Base64.getEncoder().encodeToString(captured.bytes);
            // 关键：returnAnnotatedImage = false，节省带宽
            inferenceResult = inferenceClient.detectByBase64(base64Image, confidence, false);
        } catch (Exception e) {
            if (Thread.currentThread().isInterrupted()) {
                throw new RuntimeException("检测被中断", e);
            }
            log.error("调用推理服务失败: cameraId={}", cameraId, e);
            throw new BusinessException(40088, "推理服务不可用: " + e.getMessage());
        }

        // 4. 解析推理结果（只有detections，没有annotatedImage）
        CameraDetectResponse.InferenceResult parsedResult = parseInferenceResult(inferenceResult);

        // 5. 构建响应（使用实际抽帧分辨率）
        CameraDetectResponse response = CameraDetectResponse.builder()
                .cameraId(cameraId)
                .cameraName(camera.getName())
                .captureTime(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .captureUrl(captureUrl)
                .imageWidth(captured.width)
                .imageHeight(captured.height)
                .inference(parsedResult)
                .build();

        // 6. 推送检测框坐标到WebSocket（前端用于Canvas绘制）
        pushDetectionsToWebSocket(camera, parsedResult, captured.width, captured.height);

        return response;
    }

    @Override
    public CameraCaptureVO capture(String cameraId, CameraCaptureRequest request) {
        Camera camera = cameraMapper.selectById(cameraId);
        if (camera == null) {
            throw new BusinessException(40087, "摄像头不存在");
        }
        if (!"ONLINE".equals(camera.getStatus())) {
            throw new BusinessException(40082, "摄像头离线，无法抓拍");
        }

        String rtspUrl = camera.getRtspUrl();
        if (rtspUrl == null || rtspUrl.isEmpty()) {
            throw new BusinessException(40084, "摄像头RTSP地址未配置");
        }

        CapturedFrame captured;
        try {
            captured = captureFrameFromRtsp(rtspUrl);
        } catch (Exception e) {
            log.error("抓拍失败: cameraId={}", cameraId, e);
            throw new BusinessException(40084, "抓拍失败: " + e.getMessage());
        }

        String imageUrl = saveCaptureImage(cameraId, captured.bytes);
        String capturedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        camera.setLastFrameAt(LocalDateTime.now());
        cameraMapper.updateById(camera);

        // 提交推理（可选）
        String inferenceTaskId = null;
        if (Boolean.TRUE.equals(request.getSubmitInference())) {
            try {
                String base64Image = Base64.getEncoder().encodeToString(captured.bytes);
                JsonNode result = inferenceClient.detectByBase64(base64Image, 0.5f, false);

                CameraDetectResponse.InferenceResult parsedResult = parseInferenceResult(result);
                pushDetectionsToWebSocket(camera, parsedResult, captured.width, captured.height);

                inferenceTaskId = UUID.randomUUID().toString();
            } catch (Exception e) {
                log.warn("抓拍推理失败（不影响抓拍结果）: {}", e.getMessage());
            }
        }

        return CameraCaptureVO.builder()
                .imageUrl(imageUrl)
                .capturedAt(capturedAt)
                .inferenceTaskId(inferenceTaskId)
                .build();
    }

    @Override
    public CameraBatchCaptureVO batchCapture(CameraBatchCaptureRequest request) {
        List<CameraBatchCaptureVO.BatchCaptureItem> results = new ArrayList<>();

        for (String cameraId : request.getCameraIds()) {
            try {
                CameraCaptureRequest captureReq = new CameraCaptureRequest();
                captureReq.setSubmitInference(request.getSubmitInference());

                CameraCaptureVO captureResult = capture(cameraId, captureReq);
                Camera camera = cameraMapper.selectById(cameraId);

                results.add(CameraBatchCaptureVO.BatchCaptureItem.builder()
                        .cameraId(cameraId)
                        .cameraName(camera != null ? camera.getName() : "未知")
                        .success(true)
                        .imageUrl(captureResult.getImageUrl())
                        .capturedAt(captureResult.getCapturedAt())
                        .inferenceTaskId(captureResult.getInferenceTaskId())
                        .build());
            } catch (Exception e) {
                log.warn("批量抓拍失败: cameraId={}, error={}", cameraId, e.getMessage());
                results.add(CameraBatchCaptureVO.BatchCaptureItem.builder()
                        .cameraId(cameraId)
                        .success(false)
                        .error(e.getMessage())
                        .build());
            }
        }

        return CameraBatchCaptureVO.builder()
                .results(results)
                .build();
    }

    @Override
    public void toggleMonitor(String cameraId, CameraMonitorRequest request) {
        Camera camera = cameraMapper.selectById(cameraId);
        if (camera == null) {
            throw new BusinessException(40087, "摄像头不存在");
        }

        if (Boolean.TRUE.equals(request.getEnabled())) {
            if (activeMonitors.containsKey(cameraId)) {
                log.warn("摄像头已在监测中: cameraId={}", cameraId);
                return;
            }

            int interval = request.getIntervalSeconds() != null ? request.getIntervalSeconds() : 5;
            if (interval < 5) interval = 5;

            ScheduledFuture<?> future = monitorScheduler.scheduleAtFixedRate(() -> {
                if (Thread.currentThread().isInterrupted()) return;
                try {
                    log.info("定时监测开始: cameraId={}", cameraId);
                    CameraDetectRequest detectRequest = new CameraDetectRequest();
                    detectRequest.setConfidence(request.getConfidence());
                    detectRequest.setUseSubStream(request.getUseSubStream());
                    detectRequest.setSaveCapture(false); // 定时监测不保存图片，减少IO

                    detect(cameraId, detectRequest);
                    log.info("定时监测完成: cameraId={}", cameraId);
                } catch (Exception e) {
                    if (Thread.currentThread().isInterrupted()) {
                        log.info("定时监测被中断: cameraId={}", cameraId);
                        return;
                    }
                    log.warn("定时监测失败: cameraId={}, error={}", cameraId, e.getMessage(), e);
                }
            }, 0, interval, TimeUnit.SECONDS);

            activeMonitors.put(cameraId, future);
            log.info("启动实时监测: cameraId={}, interval={}s", cameraId, interval);

        } else {
            ScheduledFuture<?> future = activeMonitors.remove(cameraId);
            if (future != null) {
                future.cancel(true);
                log.info("停止实时监测: cameraId={}", cameraId);
            }
        }
    }

    /**
     * 从RTSP流抽帧（JavaCV FFmpegFrameGrabber）
     * 使用摄像头源流分辨率，不强制缩放
     */
    private CapturedFrame captureFrameFromRtsp(String rtspUrl) throws Exception {
        log.info("从RTSP流抽帧: {}", rtspUrl);

        FFmpegFrameGrabber grabber = null;
        try {
            grabber = new FFmpegFrameGrabber(rtspUrl);
            grabber.setOption("rtsp_transport", captureTransport);
            // stimeout 控制 RTSP TCP 连接超时（微秒）
            grabber.setOption("stimeout", String.valueOf(captureTimeoutMs * 1000));
            grabber.setOption("buffer_size", "1024000");
            // 限制流分析阶段的探测大小和时长，避免 avformat_find_stream_info 阻塞
            grabber.setOption("probesize", "32000");
            grabber.setOption("analyzeduration", "2000000");
            // 不设置 setImageWidth/setImageHeight，使用源流分辨率

            log.info("正在连接RTSP流并分析流信息...");
            grabber.start();
            log.info("RTSP流连接成功: {}x{}", grabber.getImageWidth(), grabber.getImageHeight());

            // grab() 会同时消耗视频帧和音频帧，多跳过几帧确保拿到稳定的视频帧
            for (int i = 0; i < 10; i++) {
                grabber.grab();
            }

            Frame frame = grabber.grabImage();
            if (frame == null) {
                throw new RuntimeException("无法从RTSP流获取帧");
            }

            Java2DFrameConverter converter = new Java2DFrameConverter();
            BufferedImage image = converter.convert(frame);
            if (image == null) {
                throw new RuntimeException("帧转换失败");
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "jpg", baos);
            log.info("RTSP抽帧成功: {}x{}, size={}bytes", image.getWidth(), image.getHeight(), baos.size());
            return new CapturedFrame(baos.toByteArray(), image.getWidth(), image.getHeight());

        } catch (Exception e) {
            log.error("RTSP抽帧异常: {}", e.getMessage());
            throw new RuntimeException("RTSP抽帧失败: " + e.getMessage(), e);
        } finally {
            if (grabber != null) {
                try {
                    grabber.stop();
                    grabber.release();
                } catch (Exception e) {
                    log.warn("关闭RTSP连接异常: {}", e.getMessage());
                }
            }
        }
    }

    private String saveCaptureImage(String cameraId, byte[] imageBytes) {
        try {
            Path saveDir = Paths.get(captureSavePath, cameraId);
            Files.createDirectories(saveDir);

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            String fileName = timestamp + ".jpg";
            Path filePath = saveDir.resolve(fileName);

            Files.write(filePath, imageBytes);
            log.info("抓拍图片已保存: {}", filePath);

            return "/images/capture/" + cameraId + "/" + fileName;
        } catch (IOException e) {
            log.error("保存抓拍图片失败: cameraId={}", cameraId, e);
            return null;
        }
    }

    /**
     * 解析推理服务响应（只解析detections，忽略annotated_image）
     */
    private CameraDetectResponse.InferenceResult parseInferenceResult(JsonNode response) {
        if (response == null || response.get("data") == null) {
            return CameraDetectResponse.InferenceResult.builder()
                    .disease(parseModelResult(null))
                    .pest(parseModelResult(null))
                    .totalElapsedMs(0.0)
                    .build();
        }

        JsonNode data = response.get("data");

        CameraDetectResponse.ModelResult diseaseResult = parseModelResult(data.get("disease"));
        CameraDetectResponse.ModelResult pestResult = parseModelResult(data.get("pest"));

        double totalElapsed = data.has("total_elapsed_ms") ? data.get("total_elapsed_ms").asDouble() : 0;

        return CameraDetectResponse.InferenceResult.builder()
                .disease(diseaseResult)
                .pest(pestResult)
                .totalElapsedMs(totalElapsed)
                .build();
    }

    private CameraDetectResponse.ModelResult parseModelResult(JsonNode modelNode) {
        if (modelNode == null) {
            return CameraDetectResponse.ModelResult.builder()
                    .detections(Collections.emptyList())
                    .count(0)
                    .elapsedMs(0.0)
                    .build();
        }

        List<CameraDetectResponse.DetectionItem> detections = new ArrayList<>();
        JsonNode detectionsNode = modelNode.get("detections");

        if (detectionsNode != null && detectionsNode.isArray()) {
            for (JsonNode det : detectionsNode) {
                JsonNode bboxNode = det.get("bbox");
                CameraDetectResponse.BBox bbox = CameraDetectResponse.BBox.builder()
                        .x(bboxNode.get("x").asInt())
                        .y(bboxNode.get("y").asInt())
                        .width(bboxNode.get("width").asInt())
                        .height(bboxNode.get("height").asInt())
                        .build();

                CameraDetectResponse.DetectionItem item = CameraDetectResponse.DetectionItem.builder()
                        .classId(det.get("class_id").asInt())
                        .className(det.get("class_name").asText())
                        .nameCn(det.has("name_cn") ? det.get("name_cn").asText() : "")
                        .confidence(det.get("confidence").asDouble())
                        .bbox(bbox)
                        .build();

                detections.add(item);
            }
        }

        return CameraDetectResponse.ModelResult.builder()
                .detections(detections)
                .count(modelNode.has("count") ? modelNode.get("count").asInt() : detections.size())
                .elapsedMs(modelNode.has("elapsed_ms") ? modelNode.get("elapsed_ms").asDouble() : 0.0)
                .build();
    }

    /**
     * 推送检测框坐标到WebSocket
     * 前端接收后在Canvas上绘制检测框（覆盖在HLS视频上方）
     */
    private void pushDetectionsToWebSocket(Camera camera, CameraDetectResponse.InferenceResult result,
                                            int frameWidth, int frameHeight) {
        try {
            // 合并病害和虫害检测结果
            List<Map<String, Object>> allDetections = new ArrayList<>();

            if (result.getDisease() != null && result.getDisease().getDetections() != null) {
                for (CameraDetectResponse.DetectionItem det : result.getDisease().getDetections()) {
                    allDetections.add(buildDetectionMap(det, "disease"));
                }
            }
            if (result.getPest() != null && result.getPest().getDetections() != null) {
                for (CameraDetectResponse.DetectionItem det : result.getPest().getDetections()) {
                    allDetections.add(buildDetectionMap(det, "pest"));
                }
            }

            Map<String, Object> wsData = new HashMap<>();
            wsData.put("inferenceId", UUID.randomUUID().toString());
            wsData.put("cameraId", camera.getId());
            wsData.put("cameraName", camera.getName());
            wsData.put("captureTime", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            // 抽帧分辨率，前端用于坐标映射
            wsData.put("frameWidth", frameWidth);
            wsData.put("frameHeight", frameHeight);
            wsData.put("detections", allDetections);
            wsData.put("diseaseCount", result.getDisease() != null ? result.getDisease().getCount() : 0);
            wsData.put("pestCount", result.getPest() != null ? result.getPest().getCount() : 0);

            log.info("WebSocket推送检测数据: cameraId={}, frame={}x{}, disease={}, pest={}, detections={}",
                    camera.getId(), frameWidth, frameHeight,
                    wsData.get("diseaseCount"), wsData.get("pestCount"),
                    new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(allDetections));

            // 推送到通用推理结果topic
            webSocketService.sendInferenceResult(wsData);

            // 推送到摄像头专属topic
            webSocketService.sendToTopic("/topic/camera/" + camera.getId() + "/detect",
                    new com.agriculture.common.websocket.WebSocketMessage<>(
                            com.agriculture.common.websocket.WebSocketMessageType.INFERENCE_RESULT,
                            wsData));

        } catch (Exception e) {
            log.warn("推送检测结果到 WebSocket 失败: {}", e.getMessage());
        }
    }

    private Map<String, Object> buildDetectionMap(CameraDetectResponse.DetectionItem det, String type) {
        Map<String, Object> map = new HashMap<>();
        map.put("type", type); // disease 或 pest
        map.put("classId", det.getClassId());
        map.put("className", det.getClassName());
        map.put("nameCn", det.getNameCn());
        map.put("confidence", det.getConfidence());
        map.put("bbox", Map.of(
                "x", det.getBbox().getX(),
                "y", det.getBbox().getY(),
                "width", det.getBbox().getWidth(),
                "height", det.getBbox().getHeight()
        ));
        return map;
    }
}
