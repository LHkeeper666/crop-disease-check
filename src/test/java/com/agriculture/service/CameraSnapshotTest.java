package com.agriculture.service;

import com.agriculture.common.exception.BusinessException;
import com.agriculture.modules.camera.controller.CameraController;
import com.agriculture.modules.camera.service.CameraDetectService;
import com.agriculture.modules.camera.service.CameraService;
import com.agriculture.modules.user.mapper.SysUserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CameraSnapshotTest {

    @Mock
    private CameraService cameraService;

    @Mock
    private CameraDetectService cameraDetectService;

    @Mock
    private SysUserMapper sysUserMapper;

    @InjectMocks
    private CameraController cameraController;

    @Nested
    @DisplayName("GET /camera/{id}/snapshot")
    class SnapshotEndpoint {

        @Test
        @DisplayName("正常抓帧返回 JPEG")
        void snapshot_success_returnsJpeg() {
            byte[] fakeJpeg = new byte[]{(byte) 0xFF, (byte) 0xD8, 0x01, 0x02};
            when(cameraDetectService.captureSnapshot("cam-001")).thenReturn(fakeJpeg);

            ResponseEntity<byte[]> response = cameraController.snapshot("cam-001");

            assertEquals(200, response.getStatusCode().value());
            assertEquals(MediaType.IMAGE_JPEG, response.getHeaders().getContentType());
            assertArrayEquals(fakeJpeg, response.getBody());
        }

        @Test
        @DisplayName("摄像头不存在抛异常")
        void snapshot_cameraNotFound_throwsException() {
            when(cameraDetectService.captureSnapshot("nonexistent"))
                    .thenThrow(new BusinessException(40087, "摄像头不存在"));

            assertThrows(BusinessException.class, () ->
                    cameraController.snapshot("nonexistent"));
        }

        @Test
        @DisplayName("RTSP 连接超时抛异常")
        void snapshot_rtspTimeout_throwsException() {
            when(cameraDetectService.captureSnapshot("cam-001"))
                    .thenThrow(new BusinessException(40084, "快照抓帧失败: RTSP连接超时"));

            assertThrows(BusinessException.class, () ->
                    cameraController.snapshot("cam-001"));
        }
    }
}
