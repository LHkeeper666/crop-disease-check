package com.agriculture.modules.camera.service.impl;

import com.agriculture.common.exception.BusinessException;
import com.agriculture.modules.camera.dto.*;
import com.agriculture.modules.camera.entity.Camera;
import com.agriculture.modules.camera.entity.CameraGrid;
import com.agriculture.modules.camera.mapper.CameraGridMapper;
import com.agriculture.modules.camera.mapper.CameraMapper;
import com.agriculture.modules.camera.service.CameraDetectService;
import com.agriculture.modules.camera.service.CameraService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 摄像头服务实现
 */
@Service
public class CameraServiceImpl extends ServiceImpl<CameraMapper, Camera> implements CameraService {

    private static final Logger log = LoggerFactory.getLogger(CameraServiceImpl.class);

    private final CameraGridMapper cameraGridMapper;
    private final CameraDetectService cameraDetectService;

    /**
     * 记录摄像头RTSP连接建立时间（用于计算uptime）
     */
    private final Map<String, LocalDateTime> connectionStartTimeMap = new ConcurrentHashMap<>();

    public CameraServiceImpl(CameraGridMapper cameraGridMapper,
                             @Lazy CameraDetectService cameraDetectService) {
        this.cameraGridMapper = cameraGridMapper;
        this.cameraDetectService = cameraDetectService;
    }

    @Override
    public Page<Camera> listCameras(String status, String keyword, int page, int size) {
        LambdaQueryWrapper<Camera> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(status)) {
            wrapper.eq(Camera::getStatus, status);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Camera::getName, keyword)
                    .or().like(Camera::getRtspUrl, keyword));
        }
        wrapper.orderByDesc(Camera::getCreatedAt);

        Page<Camera> result = page(new Page<>(page, size), wrapper);

        // 填充覆盖网格关联
        List<String> cameraIds = result.getRecords().stream()
                .map(Camera::getId).collect(Collectors.toList());
        if (!cameraIds.isEmpty()) {
            LambdaQueryWrapper<CameraGrid> gridWrapper = new LambdaQueryWrapper<>();
            gridWrapper.in(CameraGrid::getCameraId, cameraIds);
            Map<String, List<String>> gridMap = cameraGridMapper.selectList(gridWrapper)
                    .stream().collect(Collectors.groupingBy(
                            CameraGrid::getCameraId,
                            Collectors.mapping(CameraGrid::getGridId, Collectors.toList())));
            result.getRecords().forEach(c ->
                    c.setCoverageGrids(gridMap.getOrDefault(c.getId(), Collections.emptyList())));
        }

        return result;
    }

    @Override
    @Transactional
    public String createCamera(CameraCreateRequest request) {
        // 检查名称唯一性
        long count = count(new LambdaQueryWrapper<Camera>()
                .eq(Camera::getName, request.getName()));
        if (count > 0) {
            throw new BusinessException(40081, "摄像头名称已存在");
        }

        // 创建摄像头实体
        Camera camera = new Camera();
        camera.setId(UUID.randomUUID().toString());
        camera.setName(request.getName());
        camera.setRtspUrl(request.getRtspUrl());
        camera.setRtspUrlSub(request.getRtspUrlSub());
        camera.setLocationX(request.getLocationX());
        camera.setLocationY(request.getLocationY());
        camera.setDirection(request.getDirection());
        camera.setCaptureResolution(request.getCaptureResolution());
        camera.setCaptureQuality(request.getCaptureQuality());
        camera.setReconnectInterval(request.getReconnectInterval());
        camera.setStatus("OFFLINE");
        camera.setCreatedAt(LocalDateTime.now());
        camera.setUpdatedAt(LocalDateTime.now());
        camera.setDeleted((byte) 0);

        save(camera);

        // 保存覆盖网格关联
        if (request.getCoverageGrids() != null && !request.getCoverageGrids().isEmpty()) {
            for (String gridId : request.getCoverageGrids()) {
                CameraGrid cg = new CameraGrid();
                cg.setCameraId(camera.getId());
                cg.setGridId(gridId);
                cameraGridMapper.insert(cg);
            }
        }

        // 异步尝试RTSP连接
        tryConnectAsync(camera.getId(), request.getRtspUrl());

        log.info("摄像头创建成功: id={}, name={}", camera.getId(), camera.getName());
        return camera.getId();
    }

    @Override
    @Transactional
    public void updateCamera(String id, CameraUpdateRequest request) {
        Camera camera = getById(id);
        if (camera == null) {
            throw new BusinessException(40087, "摄像头不存在");
        }

        // 检查名称唯一性（排除自身）
        if (StringUtils.hasText(request.getName())) {
            long count = count(new LambdaQueryWrapper<Camera>()
                    .eq(Camera::getName, request.getName())
                    .ne(Camera::getId, id));
            if (count > 0) {
                throw new BusinessException(40081, "摄像头名称已存在");
            }
            camera.setName(request.getName());
        }

        boolean rtspChanged = false;
        if (StringUtils.hasText(request.getRtspUrl())
                && !request.getRtspUrl().equals(camera.getRtspUrl())) {
            camera.setRtspUrl(request.getRtspUrl());
            rtspChanged = true;
        }

        if (request.getRtspUrlSub() != null) {
            camera.setRtspUrlSub(request.getRtspUrlSub());
        }
        if (request.getLocationX() != null) {
            camera.setLocationX(request.getLocationX());
        }
        if (request.getLocationY() != null) {
            camera.setLocationY(request.getLocationY());
        }
        if (request.getDirection() != null) {
            camera.setDirection(request.getDirection());
        }
        if (request.getCaptureResolution() != null) {
            camera.setCaptureResolution(request.getCaptureResolution());
        }
        if (request.getCaptureQuality() != null) {
            camera.setCaptureQuality(request.getCaptureQuality());
        }
        if (request.getReconnectInterval() != null) {
            camera.setReconnectInterval(request.getReconnectInterval());
        }

        camera.setUpdatedAt(LocalDateTime.now());
        updateById(camera);

        // 更新覆盖网格
        if (request.getCoverageGrids() != null) {
            cameraGridMapper.delete(new LambdaQueryWrapper<CameraGrid>()
                    .eq(CameraGrid::getCameraId, id));
            for (String gridId : request.getCoverageGrids()) {
                CameraGrid cg = new CameraGrid();
                cg.setCameraId(id);
                cg.setGridId(gridId);
                cameraGridMapper.insert(cg);
            }
        }

        // 如果RTSP地址变了，重新连接
        if (rtspChanged) {
            log.info("RTSP地址变更，重新连接: cameraId={}", id);
            tryConnectAsync(id, camera.getRtspUrl());
        }
    }

    @Override
    @Transactional
    public void deleteCamera(String id) {
        Camera camera = getById(id);
        if (camera == null) {
            throw new BusinessException(40087, "摄像头不存在");
        }

        // 停止该摄像头的实时监测任务
        try {
            CameraMonitorRequest stopRequest = new CameraMonitorRequest();
            stopRequest.setEnabled(false);
            cameraDetectService.toggleMonitor(id, stopRequest);
            log.info("已停止摄像头监测任务: cameraId={}", id);
        } catch (Exception e) {
            log.warn("停止监测任务时出错（可忽略）: {}", e.getMessage());
        }

        // 删除覆盖网格关联
        cameraGridMapper.delete(new LambdaQueryWrapper<CameraGrid>()
                .eq(CameraGrid::getCameraId, id));

        // 逻辑删除摄像头
        removeById(id);

        // 清理连接时间记录
        connectionStartTimeMap.remove(id);

        log.info("摄像头已删除: id={}, name={}", id, camera.getName());
    }

    @Override
    public CameraStatusVO getCameraStatus(String id) {
        Camera camera = getById(id);
        if (camera == null) {
            throw new BusinessException(40087, "摄像头不存在");
        }

        boolean isOnline = "ONLINE".equals(camera.getStatus());

        // 计算连接持续时间（uptime）
        long uptimeSeconds = 0;
        if (isOnline && connectionStartTimeMap.containsKey(id)) {
            LocalDateTime connectStart = connectionStartTimeMap.get(id);
            uptimeSeconds = Duration.between(connectStart, LocalDateTime.now()).getSeconds();
        }

        return CameraStatusVO.builder()
                .id(camera.getId())
                .status(camera.getStatus())
                .lastFrameAt(camera.getLastFrameAt() != null
                        ? camera.getLastFrameAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        : null)
                .streamUrl(isOnline ? "/stream/" + id + ".m3u8" : null)
                .connectionInfo(CameraStatusVO.ConnectionInfo.builder()
                        .protocol("RTSP")
                        .transport("TCP")
                        .uptime(uptimeSeconds)
                        .retryCount(0)
                        .build())
                .frameInfo(isOnline ? CameraStatusVO.FrameInfo.builder()
                        .fps(25)
                        .codec("H.264")
                        .build() : null)
                .build();
    }

    @Override
    public CameraBatchStatusVO batchStatus(List<String> cameraIds) {
        List<Camera> cameras = listByIds(cameraIds);

        Map<String, Camera> cameraMap = cameras.stream()
                .collect(Collectors.toMap(Camera::getId, c -> c));

        List<CameraBatchStatusVO.CameraStatusItem> items = new ArrayList<>();
        int online = 0, offline = 0, fault = 0;

        for (String cameraId : cameraIds) {
            Camera camera = cameraMap.get(cameraId);
            if (camera == null) {
                continue;
            }

            boolean isOnline = "ONLINE".equals(camera.getStatus());
            if (isOnline) online++;
            else if ("FAULT".equals(camera.getStatus())) fault++;
            else offline++;

            items.add(CameraBatchStatusVO.CameraStatusItem.builder()
                    .id(camera.getId())
                    .name(camera.getName())
                    .status(camera.getStatus())
                    .lastFrameAt(camera.getLastFrameAt() != null
                            ? camera.getLastFrameAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                            : null)
                    .streamUrl(isOnline ? "/stream/" + cameraId + ".m3u8" : null)
                    .build());
        }

        return CameraBatchStatusVO.builder()
                .statuses(items)
                .summary(CameraBatchStatusVO.StatusSummary.builder()
                        .total(items.size())
                        .online(online)
                        .offline(offline)
                        .fault(fault)
                        .build())
                .build();
    }

    @Override
    public void updateCaptureConfig(String id, CameraCaptureConfigRequest request) {
        Camera camera = getById(id);
        if (camera == null) {
            throw new BusinessException(40087, "摄像头不存在");
        }

        if (request.getCaptureResolution() != null) {
            camera.setCaptureResolution(request.getCaptureResolution());
        }
        if (request.getCaptureQuality() != null) {
            if (request.getCaptureQuality() < 1 || request.getCaptureQuality() > 100) {
                throw new BusinessException(40080, "抓拍质量范围应为1-100");
            }
            camera.setCaptureQuality(request.getCaptureQuality());
        }
        if (request.getReconnectInterval() != null) {
            if (request.getReconnectInterval() < 10 || request.getReconnectInterval() > 300) {
                throw new BusinessException(40080, "重连间隔范围应为10-300秒");
            }
            camera.setReconnectInterval(request.getReconnectInterval());
        }

        camera.setUpdatedAt(LocalDateTime.now());
        updateById(camera);
    }

    /**
     * 记录连接建立时间（供外部调用）
     */
    public void recordConnectionStart(String cameraId) {
        connectionStartTimeMap.put(cameraId, LocalDateTime.now());
    }

    /**
     * 清除连接记录（供外部调用）
     */
    public void clearConnectionRecord(String cameraId) {
        connectionStartTimeMap.remove(cameraId);
    }

    /**
     * 异步尝试RTSP连接
     */
    private void tryConnectAsync(String cameraId, String rtspUrl) {
        new Thread(() -> {
            try {
                java.net.URI uri = new java.net.URI(rtspUrl);
                String host = uri.getHost();
                int port = uri.getPort() > 0 ? uri.getPort() : 554;

                try (java.net.Socket socket = new java.net.Socket()) {
                    socket.connect(new java.net.InetSocketAddress(host, port), 5000);
                    // 连接成功，更新状态为ONLINE
                    Camera camera = getById(cameraId);
                    if (camera != null) {
                        camera.setStatus("ONLINE");
                        camera.setLastOnlineAt(LocalDateTime.now());
                        updateById(camera);
                        // 记录连接建立时间
                        recordConnectionStart(cameraId);
                        log.info("RTSP连接成功，状态已更新为ONLINE: cameraId={}", cameraId);
                    }
                }
            } catch (Exception e) {
                // 连接失败，状态保持OFFLINE
                log.warn("RTSP连接失败: cameraId={}, error={}", cameraId, e.getMessage());
                Camera camera = getById(cameraId);
                if (camera != null) {
                    camera.setStatus("OFFLINE");
                    updateById(camera);
                }
            }
        }, "rtsp-connect-" + cameraId).start();
    }

    @Override
    public void reconnect(String cameraId) {
        Camera camera = getById(cameraId);
        if (camera == null) {
            throw new BusinessException(40087, "摄像头不存在");
        }
        if (camera.getRtspUrl() == null || camera.getRtspUrl().isEmpty()) {
            throw new BusinessException(40084, "摄像头RTSP地址未配置");
        }
        log.info("手动重连摄像头: cameraId={}", cameraId);
        tryConnectAsync(cameraId, camera.getRtspUrl());
    }

    /**
     * 定时重连OFFLINE摄像头（每30秒）
     */
    @Scheduled(fixedDelay = 30000, initialDelay = 10000)
    public void autoReconnectOfflineCameras() {
        LambdaQueryWrapper<Camera> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Camera::getStatus, "OFFLINE");
        wrapper.isNotNull(Camera::getRtspUrl);
        wrapper.ne(Camera::getRtspUrl, "");
        java.util.List<Camera> offlineCameras = list(wrapper);

        for (Camera camera : offlineCameras) {
            try {
                java.net.URI uri = new java.net.URI(camera.getRtspUrl());
                String host = uri.getHost();
                int port = uri.getPort() > 0 ? uri.getPort() : 554;

                try (java.net.Socket socket = new java.net.Socket()) {
                    socket.connect(new java.net.InetSocketAddress(host, port), 3000);
                    camera.setStatus("ONLINE");
                    camera.setLastOnlineAt(LocalDateTime.now());
                    updateById(camera);
                    recordConnectionStart(camera.getId());
                    log.info("自动重连成功: cameraId={}, name={}", camera.getId(), camera.getName());
                }
            } catch (Exception ignored) {
                // 仍然离线，不更新状态
            }
        }
    }
}
