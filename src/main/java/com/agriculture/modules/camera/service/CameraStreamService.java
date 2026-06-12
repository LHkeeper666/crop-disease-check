package com.agriculture.modules.camera.service;

/**
 * 摄像头HLS流媒体服务
 * 管理RTSP → HLS转码进程
 */
public interface CameraStreamService {

    /**
     * 启动HLS转码
     *
     * @param cameraId 摄像头ID
     * @param rtspUrl  RTSP地址
     */
    void startStream(String cameraId, String rtspUrl);

    /**
     * 停止HLS转码
     *
     * @param cameraId 摄像头ID
     */
    void stopStream(String cameraId);

    /**
     * 是否正在转码
     */
    boolean isStreaming(String cameraId);

    /**
     * 获取m3u8文件路径（相对于HLS输出目录）
     */
    String getPlaylistPath(String cameraId);
}
