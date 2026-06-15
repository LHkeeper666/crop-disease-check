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
 * - 前端负责：播放MJPEG视频流 + Canvas叠加层绘制检测框
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

    // ======================== 监测会话管理 ========================

    /**
     * 活跃的监测会话 Map<cameraId, MonitorSession>
     * 每个摄像头最多一个会话，会话独占该摄像头的监测生命周期
     */
    private final Map<String, MonitorSession> sessions = new ConcurrentHashMap<>();

    /**
     * 单摄像头监测会话。
     *
     * 设计原则：
     * - 一个线程驱动完整生命周期（连接→抽帧→推理→等待→循环）
     * - 依赖 ffmpeg 自身超时(stimeout/rw_timeout)防止 native 阻塞，不做跨线程 close
     * - 失败后指数退避重连（5s → 10s → 20s，上限30s），成功后重置
     * - 停止仅需设置 volatile flag + interrupt，线程在下一个检查点安全退出
     */
    private class MonitorSession extends Thread {
        final String cameraId;
        final int intervalSec;
        final float confidence;
        final boolean useSubStream;
        volatile boolean stopped;
        /** 当前活跃的 grabber，仅用于监控/诊断，不做跨线程 close */
        volatile FFmpegFrameGrabber activeGrabber;
        int consecutiveFailures;

        MonitorSession(String cameraId, int intervalSec, float confidence, boolean useSubStream) {
            super("camera-monitor-" + cameraId);
            this.cameraId = cameraId;
            this.intervalSec = intervalSec;
            this.confidence = confidence;
            this.useSubStream = useSubStream;
        }

        @Override
        public void run() {
            log.info("监测会话启动: cameraId={}, interval={}s", cameraId, intervalSec);
            while (!stopped) {
                try {
                    // --- 退避等待 ---
                    if (consecutiveFailures > 0) {
                        long backoffMs = backoffMs();
                        log.info("退避等待 {}ms 后重连 (第{}次失败): cameraId={}",
                                backoffMs, consecutiveFailures, cameraId);
                        if (!sleepUntilStopped(backoffMs)) break; // 被停止则退出
                    }

                    // --- 执行一次检测 ---
                    CameraDetectRequest req = new CameraDetectRequest();
                    req.setConfidence(confidence);
                    req.setUseSubStream(useSubStream);
                    req.setSaveCapture(false);
                    detect(cameraId, req);

                    // 成功 → 重置退避
                    consecutiveFailures = 0;
                    log.info("监测检测完成: cameraId={}", cameraId);

                    // --- 等待到下一个间隔 ---
                    if (!sleepUntilStopped(intervalSec * 1000L)) break;

                } catch (Exception e) {
                    if (stopped) break;
                    consecutiveFailures++;
                    log.warn("监测失败 (第{}次): cameraId={}, error={}",
                            consecutiveFailures, cameraId, e.getMessage());
                }
            }
            // 确保资源释放
            releaseGrabber();
            sessions.remove(cameraId, this); // 仅当自己是当前会话时才移除
            log.info("监测会话结束: cameraId={}", cameraId);
        }

        /** 指数退避：5s → 10s → 20s，上限30s */
        private long backoffMs() {
            int n = Math.min(consecutiveFailures, 4);
            return Math.min(5000L * (1L << (n - 1)), 30000L);
        }

        /** 带停止检测的 sleep，返回 false 表示被停止 */
        private boolean sleepUntilStopped(long totalMs) {
            long remaining = totalMs;
            while (remaining > 0 && !stopped) {
                try {
                    Thread.sleep(Math.min(remaining, 200));
                    remaining -= 200;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return !stopped;
                }
            }
            return !stopped;
        }

        void releaseGrabber() {
            FFmpegFrameGrabber g = activeGrabber;
            activeGrabber = null;
            if (g != null) {
                try { g.stop(); } catch (Exception ignored) {}
                try { g.release(); } catch (Exception ignored) {}
            }
        }
    }

    // ======================== 构造函数 ========================

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
//        if (!"ONLINE".equals(camera.getStatus())) {
//            throw new BusinessException(40082, "摄像头离线，无法抓拍");
//        }

        String rtspUrl = Boolean.TRUE.equals(request.getUseSubStream()) && camera.getRtspUrlSub() != null
                ? camera.getRtspUrlSub()
                : camera.getRtspUrl();

        if (rtspUrl == null || rtspUrl.isEmpty()) {
            throw new BusinessException(40084, "摄像头RTSP地址未配置");
        }

        // 1. 从RTSP流抽帧（使用源流分辨率，不强制缩放）
        CapturedFrame captured;
        try {
            captured = captureFrameFromRtsp(cameraId, rtspUrl);
        } catch (Exception e) {
            log.error("RTSP抽帧失败: cameraId={}, rtspUrl={}", cameraId, rtspUrl, e);
            camera.setStatus("FAULT");
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
    public void toggleMonitor(String cameraId, CameraMonitorRequest request) {
        Camera camera = cameraMapper.selectById(cameraId);
        if (camera == null) {
            throw new BusinessException(40087, "摄像头不存在");
        }

        if (Boolean.TRUE.equals(request.getEnabled())) {
            // 检查已有会话
            MonitorSession existing = sessions.get(cameraId);
            if (existing != null && existing.isAlive()) {
                log.warn("摄像头已在监测中: cameraId={}", cameraId);
                return;
            }
            // 清理已死亡的旧会话
            if (existing != null) {
                sessions.remove(cameraId, existing);
            }

            int interval = request.getIntervalSeconds() != null ? request.getIntervalSeconds() : 5;
            if (interval < 5) interval = 5;

            MonitorSession session = new MonitorSession(
                    cameraId, interval,
                    request.getConfidence() != null ? request.getConfidence() : 0.5f,
                    Boolean.TRUE.equals(request.getUseSubStream()));
            sessions.put(cameraId, session);
            session.start();
            log.info("启动实时监测: cameraId={}, interval={}s", cameraId, interval);

        } else {
            MonitorSession session = sessions.remove(cameraId);
            if (session != null) {
                session.stopped = true;
                session.interrupt();
                log.info("停止实时监测: cameraId={}", cameraId);
            }
        }
    }

    /**
     * 从RTSP流抽帧（JavaCV FFmpegFrameGrabber）。
     * 依赖 ffmpeg stimeout/rw_timeout 防止 native 阻塞，不做跨线程 close。
     */
    private CapturedFrame captureFrameFromRtsp(String cameraId, String rtspUrl) throws Exception {
        log.info("从RTSP流抽帧: cameraId={}, rtspUrl={}", cameraId, rtspUrl);

        // 获取当前会话（用于注册grabber引用 + 检查停止标志）
        MonitorSession session = sessions.get(cameraId);

        FFmpegFrameGrabber grabber = null;
        try {
            grabber = new FFmpegFrameGrabber(rtspUrl);
            grabber.setOption("rtsp_transport", captureTransport);
            // stimeout: TCP socket 连接超时（微秒），默认5秒
            grabber.setOption("stimeout", String.valueOf(captureTimeoutMs * 1000L));
            // rw_timeout: 读写超时（微秒），覆盖 RTSP 协议交互阶段
            grabber.setOption("rw_timeout", String.valueOf(captureTimeoutMs * 1000L));
            grabber.setOption("buffer_size", "1024000");
            grabber.setOption("probesize", "32000");
            grabber.setOption("analyzeduration", "2000000");

            // 注册到会话（仅用于诊断）
            if (session != null) session.activeGrabber = grabber;

            log.info("正在连接RTSP流并分析流信息...");
            grabber.start();
            log.info("RTSP流连接成功: {}x{}", grabber.getImageWidth(), grabber.getImageHeight());

            // 跳过前几帧确保拿到稳定视频帧
            for (int i = 0; i < 10; i++) {
                if (session != null && session.stopped) {
                    throw new InterruptedException("监测已停止");
                }
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
            log.info("RTSP抽帧成功: cameraId={}, {}x{}, size={}bytes",
                    cameraId, image.getWidth(), image.getHeight(), baos.size());
            return new CapturedFrame(baos.toByteArray(), image.getWidth(), image.getHeight());

        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                throw e; // 原样抛出，让 MonitorSession.run() 识别
            }
            log.error("RTSP抽帧异常: cameraId={}, error={}", cameraId, e.getMessage());
            throw e; // 原样抛出，保留原始ffmpeg错误信息
        } finally {
            if (session != null) session.activeGrabber = null;
            if (grabber != null) {
                try { grabber.stop(); } catch (Exception ignored) {}
                try { grabber.release(); } catch (Exception ignored) {}
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
     * 前端接收后在Canvas上绘制检测框（覆盖在MJPEG视频上方）
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
