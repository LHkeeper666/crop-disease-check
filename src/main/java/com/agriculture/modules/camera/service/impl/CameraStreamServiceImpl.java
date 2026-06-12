package com.agriculture.modules.camera.service.impl;

import com.agriculture.modules.camera.service.CameraStreamService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 摄像头HLS流媒体服务实现
 *
 * 使用FFmpeg将RTSP流转码为HLS（m3u8 + ts分片），前端通过HLS.js播放。
 */
@Service
public class CameraStreamServiceImpl implements CameraStreamService {

    private static final Logger log = LoggerFactory.getLogger(CameraStreamServiceImpl.class);

    @Value("${hls.output-path:./hls}")
    private String hlsOutputPath;

    @Value("${hls.segment-time:4}")
    private int segmentTime;

    @Value("${hls.list-size:5}")
    private int listSize;

    /** 活跃的FFmpeg进程 Map<cameraId, Process> */
    private final Map<String, Process> activeProcesses = new ConcurrentHashMap<>();

    @Override
    public void startStream(String cameraId, String rtspUrl) {
        if (isStreaming(cameraId)) {
            log.warn("摄像头已在转码中，先停止旧进程: cameraId={}", cameraId);
            stopStream(cameraId);
        }

        try {
            Path outputDir = Paths.get(hlsOutputPath, cameraId);
            Files.createDirectories(outputDir);

            // 清理旧的HLS文件
            cleanHlsFiles(outputDir);

            Path playlistPath = outputDir.resolve(cameraId + ".m3u8");

            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg",
                    "-y",
                    "-rtsp_transport", "tcp",
                    "-i", rtspUrl,
                    "-c:v", "libx264",
                    "-preset", "ultrafast",
                    "-tune", "zerolatency",
                    "-g", String.valueOf(segmentTime * 25),
                    "-sc_threshold", "0",
                    "-f", "hls",
                    "-hls_time", String.valueOf(segmentTime),
                    "-hls_list_size", String.valueOf(listSize),
                    "-hls_flags", "delete_segments+append_list",
                    "-hls_segment_filename", outputDir.resolve(cameraId + "-%03d.ts").toString(),
                    playlistPath.toString()
            );
            pb.redirectErrorStream(true);

            Process process = pb.start();
            activeProcesses.put(cameraId, process);

            // 异步读取FFmpeg输出，记录关键日志
            new Thread(() -> {
                try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.contains("error") || line.contains("Error") || line.contains("fail") || line.contains("Failed")) {
                            log.error("FFmpeg [{}]: {}", cameraId, line);
                        } else if (line.contains("Opening") || line.contains("Stream #")) {
                            log.info("FFmpeg [{}]: {}", cameraId, line);
                        }
                    }
                } catch (IOException ignored) {}
            }, "ffmpeg-log-" + cameraId).start();

            // 监控进程退出
            new Thread(() -> {
                try {
                    int exitCode = process.waitFor();
                    log.warn("FFmpeg进程退出: cameraId={}, exitCode={}", cameraId, exitCode);
                    activeProcesses.remove(cameraId);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }, "ffmpeg-watch-" + cameraId).start();

            log.info("HLS转码已启动: cameraId={}, output={}", cameraId, playlistPath);

        } catch (IOException e) {
            log.error("启动HLS转码失败: cameraId={}", cameraId, e);
            throw new RuntimeException("启动HLS转码失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void stopStream(String cameraId) {
        Process process = activeProcesses.remove(cameraId);
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
            log.info("HLS转码已停止: cameraId={}", cameraId);
        }
    }

    @Override
    public boolean isStreaming(String cameraId) {
        Process process = activeProcesses.get(cameraId);
        return process != null && process.isAlive();
    }

    @Override
    public String getPlaylistPath(String cameraId) {
        return Paths.get(hlsOutputPath, cameraId, cameraId + ".m3u8").toString();
    }

    @PreDestroy
    public void destroy() {
        log.info("停止所有HLS转码进程, count={}", activeProcesses.size());
        activeProcesses.forEach((id, process) -> {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        });
        activeProcesses.clear();
    }

    private void cleanHlsFiles(Path dir) {
        try {
            if (Files.isDirectory(dir)) {
                Files.list(dir)
                        .filter(p -> p.toString().endsWith(".m3u8") || p.toString().endsWith(".ts"))
                        .forEach(p -> {
                            try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                        });
            }
        } catch (IOException ignored) {}
    }
}
