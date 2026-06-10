package com.agriculture.service.impl;

import com.agriculture.dao.mapper.CameraMapper;
import com.agriculture.dto.CameraDetectRequest;
import com.agriculture.dto.CameraDetectResponse;
import com.agriculture.entity.Camera;
import com.agriculture.exception.BusinessException;
import com.agriculture.service.CameraDetectService;
import com.agriculture.service.InferenceClient;
import com.agriculture.service.WorkOrderService;
import com.agriculture.websocket.WebSocketService;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.Socket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 摄像头实时识别服务实现
 */
@Service
public class CameraDetectServiceImpl implements CameraDetectService {

    private static final Logger log = LoggerFactory.getLogger(CameraDetectServiceImpl.class);

    private final CameraMapper cameraMapper;
    private final InferenceClient inferenceClient;
    private final WorkOrderService workOrderService;
    private final WebSocketService webSocketService;

    @Value("${capture.save-path:./captures}")
    private String captureSavePath;

    @Value("${capture.timeout-ms:10000}")
    private int captureTimeoutMs;

    @Value("${capture.transport:tcp}")
    private String captureTransport;

    public CameraDetectServiceImpl(CameraMapper cameraMapper,
                                   InferenceClient inferenceClient,
                                   WorkOrderService workOrderService,
                                   WebSocketService webSocketService) {
        this.cameraMapper = cameraMapper;
        this.inferenceClient = inferenceClient;
        this.workOrderService = workOrderService;
        this.webSocketService = webSocketService;
    }

    @Override
    public CameraDetectResponse detect(String cameraId, CameraDetectRequest request) {
        // 1. 查询摄像头信息
        Camera camera = cameraMapper.selectById(cameraId);
        if (camera == null) {
            throw new BusinessException(40087, "摄像头不存在");
        }

        // 2. 检查摄像头状态
        if (!"ONLINE".equals(camera.getStatus())) {
            throw new BusinessException(40082, "摄像头离线，无法抓拍");
        }

        // 3. 获取RTSP地址
        String rtspUrl = Boolean.TRUE.equals(request.getUseSubStream()) && camera.getRtspUrlSub() != null
                ? camera.getRtspUrlSub()
                : camera.getRtspUrl();

        if (rtspUrl == null || rtspUrl.isEmpty()) {
            throw new BusinessException(40084, "摄像头RTSP地址未配置");
        }

        // 4. 从RTSP流抽帧
        byte[] frameBytes;
        try {
            frameBytes = captureFrameFromRtsp(rtspUrl);
        } catch (Exception e) {
            log.error("RTSP抽帧失败: cameraId={}, rtspUrl={}", cameraId, rtspUrl, e);
            throw new BusinessException(40084, "抓拍失败: " + e.getMessage());
        }

        // 5. 保存抓拍图片
        String captureUrl = null;
        if (Boolean.TRUE.equals(request.getSaveCapture())) {
            captureUrl = saveCaptureImage(cameraId, frameBytes);
        }

        // 6. 调用推理服务
        float confidence = request.getConfidence() != null ? request.getConfidence() : 0.5f;
        boolean returnAnnotated = Boolean.TRUE.equals(request.getReturnAnnotatedImage());

        JsonNode inferenceResult;
        try {
            String base64Image = Base64.getEncoder().encodeToString(frameBytes);
            inferenceResult = inferenceClient.detectByBase64(base64Image, confidence, returnAnnotated);
        } catch (Exception e) {
            log.error("调用推理服务失败: cameraId={}", cameraId, e);
            throw new BusinessException(40088, "推理服务不可用: " + e.getMessage());
        }

        // 7. 解析推理结果
        CameraDetectResponse.InferenceResult parsedResult = parseInferenceResult(inferenceResult);

        // 8. 根据置信度创建工单
        CameraDetectResponse.WorkOrderInfo workOrderInfo = createWorkOrderIfNeeded(camera, parsedResult);

        // 9. 构建响应
        CameraDetectResponse response = CameraDetectResponse.builder()
                .cameraId(cameraId)
                .cameraName(camera.getName())
                .captureTime(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .captureUrl(captureUrl)
                .inference(parsedResult)
                .workOrder(workOrderInfo)
                .build();

        // 10. 推送推理结果到 WebSocket
        try {
            Map<String, Object> wsData = new HashMap<>();
            wsData.put("inferenceId", UUID.randomUUID().toString());
            wsData.put("cameraName", camera.getName());
            wsData.put("captureTime", response.getCaptureTime());
            wsData.put("diseaseCount", parsedResult.getDisease() != null ? parsedResult.getDisease().getCount() : 0);
            wsData.put("pestCount", parsedResult.getPest() != null ? parsedResult.getPest().getCount() : 0);
            wsData.put("workOrderCreated", Boolean.TRUE.equals(workOrderInfo.getCreated()));
            webSocketService.sendInferenceResult(wsData);
        } catch (Exception e) {
            log.warn("推送推理结果到 WebSocket 失败: {}", e.getMessage());
        }

        return response;
    }

    /**
     * 从RTSP流抽帧（简化实现，实际生产环境建议使用JavaCV/FFmpeg）
     */
    private byte[] captureFrameFromRtsp(String rtspUrl) throws Exception {
        // 简化实现：验证RTSP地址可达性
        // 实际生产环境应使用JavaCV的FFmpegFrameGrabber进行抽帧
        // 这里提供一个基础的连接验证，真正的抽帧逻辑需要引入javacv依赖

        log.info("尝试从RTSP流抽帧: {}", rtspUrl);

        // 解析RTSP地址
        URI uri = new URI(rtspUrl);
        String host = uri.getHost();
        int port = uri.getPort() > 0 ? uri.getPort() : 554;

        // 验证连接可达性
        try (Socket socket = new Socket()) {
            socket.connect(new java.net.InetSocketAddress(host, port), captureTimeoutMs);
            log.info("RTSP连接成功: {}:{}", host, port);
        } catch (Exception e) {
            throw new RuntimeException("无法连接到RTSP流: " + e.getMessage());
        }

        // TODO: 使用JavaCV进行实际抽帧
        // FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(rtspUrl);
        // grabber.setOption("rtsp_transport", "tcp");
        // grabber.start();
        // Frame frame = grabber.grabImage();
        // BufferedImage image = new Java2DFrameConverter().convert(frame);
        // ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // ImageIO.write(image, "jpg", baos);
        // grabber.stop();
        // return baos.toByteArray();

        // 临时返回空数组，待集成JavaCV后替换
        throw new RuntimeException("RTSP抽帧功能需要集成JavaCV依赖，请在pom.xml中添加javacv-platform依赖");
    }

    /**
     * 保存抓拍图片到本地
     */
    private String saveCaptureImage(String cameraId, byte[] imageBytes) {
        try {
            // 创建保存目录
            Path saveDir = Paths.get(captureSavePath);
            Files.createDirectories(saveDir);

            // 生成文件名
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            String fileName = String.format("%s-%s.jpg", timestamp, cameraId);
            Path filePath = saveDir.resolve(fileName);

            // 保存文件
            Files.write(filePath, imageBytes);

            log.info("抓拍图片已保存: {}", filePath);
            return "/images/capture/" + fileName;
        } catch (Exception e) {
            log.error("保存抓拍图片失败: cameraId={}", cameraId, e);
            return null;
        }
    }

    /**
     * 解析推理服务响应
     */
    private CameraDetectResponse.InferenceResult parseInferenceResult(JsonNode response) {
        JsonNode data = response.get("data");

        CameraDetectResponse.ModelResult diseaseResult = parseModelResult(data.get("disease"));
        CameraDetectResponse.ModelResult pestResult = parseModelResult(data.get("pest"));

        String annotatedImage = null;
        if (data.has("annotated_image") && !data.get("annotated_image").isNull()) {
            annotatedImage = data.get("annotated_image").asText();
        }

        double totalElapsed = data.has("total_elapsed_ms") ? data.get("total_elapsed_ms").asDouble() : 0;

        return CameraDetectResponse.InferenceResult.builder()
                .disease(diseaseResult)
                .pest(pestResult)
                .annotatedImage(annotatedImage)
                .totalElapsedMs(totalElapsed)
                .build();
    }

    /**
     * 解析单个模型的检测结果
     */
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
     * 根据置信度创建工单
     */
    private CameraDetectResponse.WorkOrderInfo createWorkOrderIfNeeded(Camera camera,
                                                                        CameraDetectResponse.InferenceResult result) {
        // 计算最高置信度
        double maxConfidence = 0;
        String pestName = null;
        String type = null;

        // 检查病害
        if (result.getDisease() != null && result.getDisease().getDetections() != null) {
            for (CameraDetectResponse.DetectionItem det : result.getDisease().getDetections()) {
                if (det.getConfidence() > maxConfidence) {
                    maxConfidence = det.getConfidence();
                    pestName = det.getNameCn();
                    type = "disease";
                }
            }
        }

        // 检查虫害
        if (result.getPest() != null && result.getPest().getDetections() != null) {
            for (CameraDetectResponse.DetectionItem det : result.getPest().getDetections()) {
                if (det.getConfidence() > maxConfidence) {
                    maxConfidence = det.getConfidence();
                    pestName = det.getNameCn();
                    type = "pest";
                }
            }
        }

        // 根据置信度决定是否创建工单及严重程度
        if (maxConfidence < 0.6) {
            return CameraDetectResponse.WorkOrderInfo.builder()
                    .created(false)
                    .build();
        }

        String severity = maxConfidence >= 0.8 ? "CRITICAL" : "HIGH";

        // TODO: 调用WorkOrderService创建工单
        // WorkOrder workOrder = new WorkOrder();
        // workOrder.setTitle(String.format("【%s】%s 发现%s", severity, camera.getName(), pestName));
        // workOrder.setSeverity(severity);
        // workOrder.setStatus("PENDING");
        // workOrder.setType(type);
        // workOrder.setPestName(pestName);
        // workOrder.setConfidence(BigDecimal.valueOf(maxConfidence));
        // workOrder.setCompanyId(camera.getCompanyId());
        // workOrderService.save(workOrder);

        log.info("检测到病虫害: camera={}, pest={}, confidence={}, severity={}",
                camera.getName(), pestName, maxConfidence, severity);

        // 临时返回模拟工单信息
        return CameraDetectResponse.WorkOrderInfo.builder()
                .created(true)
                .workOrderId(UUID.randomUUID().toString())
                .severity(severity)
                .build();
    }
}
