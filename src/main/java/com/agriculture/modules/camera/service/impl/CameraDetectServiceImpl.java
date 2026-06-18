package com.agriculture.modules.camera.service.impl;

import com.agriculture.modules.camera.dto.*;
import com.agriculture.modules.camera.entity.Camera;
import com.agriculture.modules.camera.entity.CameraGrid;
import com.agriculture.modules.camera.mapper.CameraGridMapper;
import com.agriculture.modules.camera.mapper.CameraMapper;
import com.agriculture.modules.camera.service.CameraDetectService;
import com.agriculture.common.exception.BusinessException;
import com.agriculture.common.mq.event.DetectionEvent;
import com.agriculture.common.config.RabbitMQConfig;
import com.agriculture.modules.grid.entity.Grid;
import com.agriculture.modules.grid.mapper.GridMapper;
import com.agriculture.modules.greenhouse.entity.Greenhouse;
import com.agriculture.modules.greenhouse.mapper.GreenhouseMapper;
import com.agriculture.modules.inference.entity.Inference;
import com.agriculture.modules.inference.mapper.InferenceMapper;
import com.agriculture.modules.inference.service.InferenceClient;
import com.agriculture.common.websocket.WebSocketService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

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
    private static final ObjectMapper JSON = new ObjectMapper();

    private final CameraMapper cameraMapper;
    private final CameraGridMapper cameraGridMapper;
    private final GridMapper gridMapper;
    private final GreenhouseMapper greenhouseMapper;
    private final InferenceMapper inferenceMapper;
    private final InferenceClient inferenceClient;
    private final WebSocketService webSocketService;
    private final RabbitTemplate rabbitTemplate;

    @Value("${capture.save-path:./captures}")
    private String captureSavePath;

    @Value("${capture.timeout-ms:10000}")
    private int captureTimeoutMs;

    @Value("${capture.transport:tcp}")
    private String captureTransport;

    /** 心跳持久化间隔（分钟）：即使检测结果无变化，也至少每 N 分钟写一条 */
    @Value("${camera.detect.heartbeat-minutes:10}")
    private int heartbeatMinutes;

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

    // ======================== 变更检测快照 ========================

    /**
     * 置信度分档，用于判断是否"跨等级"
     */
    private enum SeverityLevel {
        LOW,      // < 0.4
        MEDIUM,   // 0.4 - 0.6
        HIGH,     // 0.6 - 0.8
        CRITICAL; // > 0.8

        static SeverityLevel fromConfidence(double confidence) {
            if (confidence >= 0.8) return CRITICAL;
            if (confidence >= 0.6) return HIGH;
            if (confidence >= 0.4) return MEDIUM;
            return LOW;
        }
    }

    /**
     * 每个摄像头的检测快照（内存状态）
     */
    private static class CameraSnapshot {
        /** 上次持久化的物种 → 置信度等级 */
        Map<Integer, SeverityLevel> lastDetections = new HashMap<>();
        /** 上次持久化时间 */
        LocalDateTime lastPersistedAt;
        /** 关联网格标签（从 camera_grid 查出后缓存） */
        List<String> gridLabels = Collections.emptyList();
        /** 缓存的企业ID（通过 camera→grid→greenhouse→company 解析） */
        String companyId;
        /** 上次检测结果（用于空检测时保留标签） */
        List<CameraDetectResponse.DetectionItem> lastRawDetections = Collections.emptyList();
    }

    /** 每个摄像头的检测快照 Map<cameraId, CameraSnapshot> */
    private final Map<String, CameraSnapshot> snapshots = new ConcurrentHashMap<>();

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
                                   CameraGridMapper cameraGridMapper,
                                   GridMapper gridMapper,
                                   GreenhouseMapper greenhouseMapper,
                                   InferenceMapper inferenceMapper,
                                   InferenceClient inferenceClient,
                                   WebSocketService webSocketService,
                                   RabbitTemplate rabbitTemplate) {
        this.cameraMapper = cameraMapper;
        this.cameraGridMapper = cameraGridMapper;
        this.gridMapper = gridMapper;
        this.greenhouseMapper = greenhouseMapper;
        this.inferenceMapper = inferenceMapper;
        this.inferenceClient = inferenceClient;
        this.webSocketService = webSocketService;
        this.rabbitTemplate = rabbitTemplate;
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
        } catch (InterruptedException e) {
            // 监测被用户停止，不是真正的故障，直接抛出
            Thread.currentThread().interrupt();
            throw new BusinessException(40080, "监测已停止");
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

        // 3. 调用推理服务（返回标注图+原始图URL，用于工单图片展示）
        float confidence = request.getConfidence() != null ? request.getConfidence() : 0.5f;

        JsonNode inferenceResult;
        try {
            String base64Image = Base64.getEncoder().encodeToString(captured.bytes);
            inferenceResult = inferenceClient.detectByBase64(base64Image, confidence, true);
        } catch (Exception e) {
            if (Thread.currentThread().isInterrupted()) {
                throw new RuntimeException("检测被中断", e);
            }
            log.error("调用推理服务失败: cameraId={}", cameraId, e);
            throw new BusinessException(40088, "推理服务不可用: " + e.getMessage());
        }

        // 4. 解析推理结果
        CameraDetectResponse.InferenceResult parsedResult = parseInferenceResult(inferenceResult);

        // 提取图片URL（CV服务返回的原始图和标注图MinIO地址）
        String annotatedImageUrl = null;
        String originalImageUrl = null;
        JsonNode dataNode = inferenceResult.get("data");
        if (dataNode != null) {
            if (dataNode.has("annotated_url") && !dataNode.get("annotated_url").isNull()) {
                annotatedImageUrl = dataNode.get("annotated_url").asText();
            }
            if (dataNode.has("original_url") && !dataNode.get("original_url").isNull()) {
                originalImageUrl = dataNode.get("original_url").asText();
            }
        }

        // 5. 变更检测：只在状态发生变化时持久化
        List<CameraDetectResponse.DetectionItem> allDetections = mergeAllDetections(parsedResult);
        CameraSnapshot snapshot = snapshots.get(cameraId);
        List<String> gridLabels = getGridLabelsForCamera(cameraId, snapshot);
        String companyId = resolveCompanyId(cameraId, snapshot, camera);

        if (shouldPersist(snapshot, allDetections)) {
            // 持久化到 inference 表
            String inferenceId = persistInference(cameraId, parsedResult, gridLabels, companyId, annotatedImageUrl, originalImageUrl);

            // 发送检测事件到 RabbitMQ（工单生成 + 热力图更新由消费者异步处理）
            DetectionEvent event = buildDetectionEvent(inferenceId, cameraId, parsedResult, gridLabels, companyId);
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE,
                    "detect.CAMERA",
                    event
            );
            log.info("检测事件已发送到MQ: inferenceId={}, grids={}", inferenceId, gridLabels);

            // 更新内存快照
            updateSnapshot(cameraId, allDetections, gridLabels);

            log.info("检测状态变更，已持久化: cameraId={}, detections={}", cameraId, allDetections.size());
        } else {
            log.debug("检测无变化，跳过持久化: cameraId={}", cameraId);
        }

        // 6. 构建响应（使用实际抽帧分辨率）
        CameraDetectResponse response = CameraDetectResponse.builder()
                .cameraId(cameraId)
                .cameraName(camera.getName())
                .captureTime(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .captureUrl(captureUrl)
                .imageWidth(captured.width)
                .imageHeight(captured.height)
                .inference(parsedResult)
                .build();

        // 7. 推送检测框坐标到WebSocket（始终执行，不受持久化影响）
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
            // 保留快照以便重启后去重，但释放原始检测数据
            CameraSnapshot snapshot = snapshots.get(cameraId);
            if (snapshot != null) {
                snapshot.lastRawDetections = Collections.emptyList();
            }
        }
    }

    @Override
    public byte[] captureSnapshot(String cameraId) {
        Camera camera = cameraMapper.selectById(cameraId);
        if (camera == null) {
            throw new BusinessException(40087, "摄像头不存在");
        }

        String rtspUrl = camera.getRtspUrl();
        if (rtspUrl == null || rtspUrl.isEmpty()) {
            throw new BusinessException(40084, "摄像头RTSP地址未配置");
        }

        try {
            CapturedFrame captured = captureFrameFromRtsp(cameraId, rtspUrl);
            return captured.bytes;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(40080, "抓帧被中断");
        } catch (Exception e) {
            log.error("快照抓帧失败: cameraId={}, error={}", cameraId, e.getMessage(), e);
            throw new BusinessException(40084, "快照抓帧失败: " + e.getMessage());
        }
    }

    /**
     * 从RTSP流抽帧（JavaCV FFmpegFrameGrabber）。
     * ffmpeg 超时选项 + Future 硬超时双重保障，防止 native 阻塞。
     */
    private CapturedFrame captureFrameFromRtsp(String cameraId, String rtspUrl) throws Exception {
        log.info("从RTSP流抽帧: cameraId={}, rtspUrl={}", cameraId, rtspUrl);

        // 获取当前会话（用于注册grabber引用 + 检查停止标志）
        MonitorSession session = sessions.get(cameraId);

        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(rtspUrl);
        try {
            grabber.setOption("rtsp_transport", captureTransport);
            // stimeout: TCP socket 连接超时（微秒）
            grabber.setOption("stimeout", String.valueOf(captureTimeoutMs * 1000L));
            // rw_timeout: 读写超时（微秒），覆盖 RTSP 协议交互阶段
            grabber.setOption("rw_timeout", String.valueOf(captureTimeoutMs * 1000L));
            // timeout: 通用 socket 超时（微秒），部分 ffmpeg 版本需要
            grabber.setOption("timeout", String.valueOf(captureTimeoutMs * 1000L));
            grabber.setOption("buffer_size", "1024000");
            grabber.setOption("probesize", "32000");
            grabber.setOption("analyzeduration", "2000000");

            // 注册到会话（仅用于诊断）
            if (session != null) session.activeGrabber = grabber;

            log.info("正在连接RTSP流并分析流信息...");
            // 用 Future 强制超时，防止 native grabber.start() 无视 ffmpeg 超时选项
            ExecutorService startExecutor = Executors.newSingleThreadExecutor();
            try {
                Future<?> startFuture = startExecutor.submit(() -> { grabber.start(); return null; });
                try {
                    startFuture.get(captureTimeoutMs + 3000L, TimeUnit.MILLISECONDS);
                } catch (TimeoutException te) {
                    try { grabber.release(); } catch (Exception ignored) {}
                    throw new RuntimeException("RTSP连接超时(" + captureTimeoutMs + "ms)");
                } catch (ExecutionException ee) {
                    Throwable cause = ee.getCause();
                    if (cause instanceof Exception) throw (Exception) cause;
                    throw new RuntimeException(cause);
                }
            } finally {
                startExecutor.shutdownNow();
            }
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

    // ======================== 变更检测 + 持久化 ========================

    /**
     * 合并病害和虫害检测结果为统一列表
     */
    private List<CameraDetectResponse.DetectionItem> mergeAllDetections(
            CameraDetectResponse.InferenceResult result) {
        List<CameraDetectResponse.DetectionItem> all = new ArrayList<>();
        if (result.getDisease() != null && result.getDisease().getDetections() != null) {
            all.addAll(result.getDisease().getDetections());
        }
        if (result.getPest() != null && result.getPest().getDetections() != null) {
            all.addAll(result.getPest().getDetections());
        }
        return all;
    }

    /**
     * 判断本次检测是否需要持久化
     */
    private boolean shouldPersist(CameraSnapshot snapshot,
                                   List<CameraDetectResponse.DetectionItem> currentDetections) {
        // 1. 首次检测，无快照
        if (snapshot == null || snapshot.lastPersistedAt == null) return true;

        // 2. 距上次持久化超过心跳间隔
        if (snapshot.lastPersistedAt.isBefore(LocalDateTime.now().minusMinutes(heartbeatMinutes))) {
            return true;
        }

        // 3. 提取当前检测的物种集合
        Set<Integer> currentIds = currentDetections.stream()
                .map(CameraDetectResponse.DetectionItem::getClassId)
                .collect(Collectors.toSet());
        Set<Integer> previousIds = snapshot.lastDetections.keySet();

        // 4. 新增物种
        if (!previousIds.containsAll(currentIds)) return true;

        // 5. 物种消失
        if (!currentIds.containsAll(previousIds)) return true;

        // 6. 置信度跨等级
        for (CameraDetectResponse.DetectionItem det : currentDetections) {
            SeverityLevel prev = snapshot.lastDetections.get(det.getClassId());
            SeverityLevel curr = SeverityLevel.fromConfidence(det.getConfidence());
            if (prev != null && prev != curr) return true;
        }

        return false;
    }

    /**
     * 获取摄像头关联的网格标签（优先从快照缓存读取）
     */
    private List<String> getGridLabelsForCamera(String cameraId, CameraSnapshot snapshot) {
        if (snapshot != null && !snapshot.gridLabels.isEmpty()) {
            return snapshot.gridLabels;
        }
        // 从 camera_grid → grid 查询
        LambdaQueryWrapper<CameraGrid> cgWrapper = new LambdaQueryWrapper<>();
        cgWrapper.eq(CameraGrid::getCameraId, cameraId);
        List<CameraGrid> cameraGrids = cameraGridMapper.selectList(cgWrapper);
        if (cameraGrids.isEmpty()) return Collections.emptyList();

        List<String> gridIds = cameraGrids.stream()
                .map(CameraGrid::getGridId).distinct().collect(Collectors.toList());

        // 先按 id 查，查不到再按 label 查（兼容 camera_grid.grid_id 存的是 label 的情况）
        LambdaQueryWrapper<Grid> gridWrapper = new LambdaQueryWrapper<>();
        gridWrapper.in(Grid::getId, gridIds);
        List<Grid> grids = gridMapper.selectList(gridWrapper);
        if (grids.isEmpty()) {
            gridWrapper = new LambdaQueryWrapper<>();
            gridWrapper.in(Grid::getLabel, gridIds);
            grids = gridMapper.selectList(gridWrapper);
        }
        List<String> labels = grids.stream()
                .map(Grid::getLabel).collect(Collectors.toList());

        // 缓存到快照
        if (snapshot != null) {
            snapshot.gridLabels = labels;
        }
        return labels;
    }

    /**
     * 解析摄像头所属企业ID，结果缓存到快照。
     * 优先从 camera.company_id 直接读取；若为空则走 camera → grid → greenhouse → company 链路。
     */
    private String resolveCompanyId(String cameraId, CameraSnapshot snapshot, Camera camera) {
        if (snapshot != null && snapshot.companyId != null) {
            return snapshot.companyId;
        }

        // 优先：camera 表自身有 company_id
        if (camera != null && camera.getCompanyId() != null && !camera.getCompanyId().isEmpty()) {
            if (snapshot != null) snapshot.companyId = camera.getCompanyId();
            return camera.getCompanyId();
        }

        // 回退：camera_grid → grid → greenhouse → company
        LambdaQueryWrapper<CameraGrid> cgWrapper = new LambdaQueryWrapper<>();
        cgWrapper.eq(CameraGrid::getCameraId, cameraId);
        List<String> gridIds = cameraGridMapper.selectList(cgWrapper).stream()
                .map(CameraGrid::getGridId).distinct().collect(Collectors.toList());
        if (gridIds.isEmpty()) return null;

        // 先按 id 查，查不到再按 label 查（兼容 camera_grid.grid_id 存的是 label 的情况）
        LambdaQueryWrapper<Grid> gridWrapper = new LambdaQueryWrapper<>();
        gridWrapper.in(Grid::getId, gridIds);
        List<Grid> grids = gridMapper.selectList(gridWrapper);
        if (grids.isEmpty()) {
            gridWrapper = new LambdaQueryWrapper<>();
            gridWrapper.in(Grid::getLabel, gridIds);
            grids = gridMapper.selectList(gridWrapper);
        }
        Set<String> greenhouseIds = grids.stream()
                .map(Grid::getGreenhouseId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (greenhouseIds.isEmpty()) return null;

        LambdaQueryWrapper<Greenhouse> ghWrapper = new LambdaQueryWrapper<>();
        ghWrapper.in(Greenhouse::getId, greenhouseIds);
        String companyId = greenhouseMapper.selectList(ghWrapper).stream()
                .map(Greenhouse::getCompanyId).filter(Objects::nonNull)
                .findFirst().orElse(null);

        if (snapshot != null) {
            snapshot.companyId = companyId;
        }
        return companyId;
    }

    /**
     * 持久化检测结果到 inference 表，返回 inferenceId
     */
    private String persistInference(String cameraId, CameraDetectResponse.InferenceResult result,
                                     List<String> gridLabels, String companyId,
                                     String annotatedImageUrl, String originalImageUrl) {
        try {
            // 构建 detections JSON
            List<Map<String, Object>> detectionsList = new ArrayList<>();
            List<Integer> diseaseIds = new ArrayList<>();
            List<Integer> pestIds = new ArrayList<>();

            if (result.getDisease() != null) {
                for (CameraDetectResponse.DetectionItem det : result.getDisease().getDetections()) {
                    detectionsList.add(buildDetectionMap(det, "DISEASE"));
                    diseaseIds.add(det.getClassId());
                }
            }
            if (result.getPest() != null) {
                for (CameraDetectResponse.DetectionItem det : result.getPest().getDetections()) {
                    detectionsList.add(buildDetectionMap(det, "PEST"));
                    pestIds.add(det.getClassId());
                }
            }

            Inference inference = new Inference();
            inference.setId(UUID.randomUUID().toString());
            inference.setSourceType("CAMERA");
            inference.setCameraId(cameraId);
            inference.setGridLabels(gridLabels.isEmpty() ? null : String.join(",", gridLabels));
            inference.setDiseaseIds(diseaseIds.isEmpty() ? null : JSON.writeValueAsString(diseaseIds));
            inference.setPestIds(pestIds.isEmpty() ? null : JSON.writeValueAsString(pestIds));
            inference.setDetections(JSON.writeValueAsString(detectionsList));
            inference.setTotalElapsedMs(result.getTotalElapsedMs() != null
                    ? BigDecimal.valueOf(result.getTotalElapsedMs()) : null);
            inference.setCompanyId(companyId);
            inference.setAnnotatedImageUrl(annotatedImageUrl);
            inference.setOriginalImageUrl(originalImageUrl);
            inference.setCreatedAt(LocalDateTime.now());

            inferenceMapper.insert(inference);
            log.info("检测结果已持久化: inferenceId={}, cameraId={}, companyId={}", inference.getId(), cameraId, companyId);
            return inference.getId();
        } catch (Exception e) {
            log.error("持久化检测结果失败: cameraId={}", cameraId, e);
            return null;
        }
    }

    /**
     * 更新内存快照
     */
    private void updateSnapshot(String cameraId, List<CameraDetectResponse.DetectionItem> detections,
                                 List<String> gridLabels) {
        CameraSnapshot snapshot = snapshots.computeIfAbsent(cameraId, k -> new CameraSnapshot());
        snapshot.lastDetections.clear();
        for (CameraDetectResponse.DetectionItem det : detections) {
            snapshot.lastDetections.put(det.getClassId(), SeverityLevel.fromConfidence(det.getConfidence()));
        }
        snapshot.lastPersistedAt = LocalDateTime.now();
        snapshot.gridLabels = gridLabels;
        snapshot.lastRawDetections = detections;
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

    /**
     * 构建 MQ 消息体
     */
    private DetectionEvent buildDetectionEvent(String inferenceId, String cameraId,
                                                CameraDetectResponse.InferenceResult result,
                                                List<String> gridLabels, String companyId) {
        DetectionEvent event = new DetectionEvent();
        event.setInferenceId(inferenceId);
        event.setCameraId(cameraId);
        event.setSourceType("CAMERA");
        event.setCompanyId(companyId);
        event.setGridLabels(gridLabels);

        List<DetectionEvent.PestDetection> detections = new ArrayList<>();
        if (result.getDisease() != null) {
            for (CameraDetectResponse.DetectionItem det : result.getDisease().getDetections()) {
                detections.add(toPestDetection(det, "disease"));
            }
        }
        if (result.getPest() != null) {
            for (CameraDetectResponse.DetectionItem det : result.getPest().getDetections()) {
                detections.add(toPestDetection(det, "pest"));
            }
        }
        event.setDetections(detections);
        return event;
    }

    private DetectionEvent.PestDetection toPestDetection(CameraDetectResponse.DetectionItem det, String type) {
        DetectionEvent.PestDetection pd = new DetectionEvent.PestDetection();
        pd.setClassId(det.getClassId());
        pd.setClassName(det.getClassName());
        pd.setNameCn(det.getNameCn());
        pd.setConfidence(det.getConfidence());
        pd.setType(type);
        return pd;
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
