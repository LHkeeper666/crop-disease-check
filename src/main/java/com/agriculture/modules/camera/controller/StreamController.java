package com.agriculture.modules.camera.controller;

import com.agriculture.modules.camera.entity.Camera;
import com.agriculture.modules.camera.mapper.CameraMapper;
import com.agriculture.modules.camera.service.CameraStreamService;
import com.agriculture.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * HLS视频流控制器
 * 提供RTSP → HLS转码后的m3u8播放列表和ts分片
 */
@RestController
@RequestMapping("/stream")
public class StreamController {

    private static final Logger log = LoggerFactory.getLogger(StreamController.class);

    private final CameraStreamService streamService;
    private final CameraMapper cameraMapper;

    public StreamController(CameraStreamService streamService, CameraMapper cameraMapper) {
        this.streamService = streamService;
        this.cameraMapper = cameraMapper;
    }

    /**
     * 获取摄像头HLS播放列表
     * GET /api/stream/{cameraId}.m3u8
     *
     * 首次请求时自动启动转码，后续请求直接返回m3u8文件
     */
    @GetMapping("/{cameraId}.m3u8")
    public void getPlaylist(@PathVariable String cameraId,
                            HttpServletResponse response) throws IOException {
        ensureStream(cameraId);

        Path playlist = Paths.get(streamService.getPlaylistPath(cameraId));
        if (!Files.exists(playlist)) {
            // FFmpeg可能还没生成第一个分片，等待一小段时间
            waitForFile(playlist, 5000);
        }

        if (!Files.exists(playlist)) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":503,\"message\":\"视频流尚未就绪，请稍后重试\"}");
            return;
        }

        response.setContentType("application/vnd.apple.mpegurl");
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Access-Control-Allow-Origin", "*");
        Files.copy(playlist, response.getOutputStream());
    }

    /**
     * 获取HLS分片文件 — 支持两种URL格式:
     * GET /api/stream/{cameraId}/{segmentName}  （子目录格式）
     * GET /api/stream/{segmentName}             （平铺格式，从segmentName解析cameraId）
     *
     * FFmpeg生成的m3u8中引用裸文件名如 c001-000.ts，
     * HLS.js相对于m3u8 URL解析后请求 /api/stream/c001-000.ts，
     * 由第二个pattern匹配，从segmentName前缀提取cameraId。
     */
    @GetMapping("/{cameraId}/{segmentName}")
    public void getSegment(@PathVariable String cameraId,
                           @PathVariable String segmentName,
                           HttpServletResponse response) throws IOException {
        serveSegment(cameraId, segmentName, response);
    }

    @GetMapping("/{segmentName}")
    public void getSegmentFlat(@PathVariable String segmentName,
                               HttpServletResponse response) throws IOException {
        // 安全校验：只允许 .ts 文件
        if (!segmentName.endsWith(".ts") || segmentName.contains("..") || segmentName.contains("/")) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        // 从 segmentName 解析 cameraId，如 "c001-000.ts" → "c001"
        int dashIdx = segmentName.indexOf('-');
        if (dashIdx <= 0) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        String cameraId = segmentName.substring(0, dashIdx);
        serveSegment(cameraId, segmentName, response);
    }

    private void serveSegment(String cameraId, String segmentName, HttpServletResponse response) throws IOException {
        if (!segmentName.endsWith(".ts") || segmentName.contains("..") || segmentName.contains("/")) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        Path segment = Paths.get("./hls", cameraId, segmentName);
        if (!Files.exists(segment)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        response.setContentType("video/mp2t");
        response.setHeader("Cache-Control", "max-age=3600");
        response.setHeader("Access-Control-Allow-Origin", "*");
        Files.copy(segment, response.getOutputStream());
    }

    /**
     * 确保转码已启动，如未启动则自动启动
     */
    private void ensureStream(String cameraId) {
        if (streamService.isStreaming(cameraId)) {
            return;
        }

        Camera camera = cameraMapper.selectById(cameraId);
        if (camera == null) {
            throw new BusinessException(40087, "摄像头不存在");
        }

        String rtspUrl = camera.getRtspUrl();
        if (rtspUrl == null || rtspUrl.isEmpty()) {
            throw new BusinessException(40084, "摄像头RTSP地址未配置");
        }

        streamService.startStream(cameraId, rtspUrl);
    }

    private void waitForFile(Path file, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (Files.exists(file)) return;
            try { Thread.sleep(200); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
