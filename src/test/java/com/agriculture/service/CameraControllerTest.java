package com.agriculture.service;

import com.agriculture.common.exception.GlobalExceptionHandler;
import com.agriculture.modules.camera.controller.CameraController;
import com.agriculture.modules.camera.dto.*;
import com.agriculture.modules.camera.entity.Camera;
import com.agriculture.modules.camera.service.CameraDetectService;
import com.agriculture.modules.camera.service.CameraService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CameraController 单元测试")
class CameraControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private CameraService cameraService;
    @Mock private CameraDetectService cameraDetectService;
    @InjectMocks private CameraController cameraController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(cameraController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new LocalValidatorFactoryBean())
                .build();
    }

    @Nested
    @DisplayName("GET /camera/list - 摄像头列表")
    class List {

        @Test
        @DisplayName("查询列表返回成功")
        void list_returnsPage() throws Exception {
            Page<Camera> page = new Page<>(1, 20, 0);
            page.setRecords(java.util.List.of());
            when(cameraService.listCameras(any(), any(), eq(1), eq(20), any())).thenReturn(page);

            mockMvc.perform(get("/camera/list"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }
    }

    @Nested
    @DisplayName("POST /camera - 添加摄像头")
    class Add {

        @Test
        @DisplayName("添加成功返回ID")
        void add_valid_returnsId() throws Exception {
            when(cameraService.createCamera(any(CameraCreateRequest.class), any())).thenReturn("cam-001");

            CameraCreateRequest req = new CameraCreateRequest();
            req.setName("测试摄像头");
            req.setRtspUrl("rtsp://192.168.1.100:554/stream1");

            mockMvc.perform(post("/camera")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.id").value("cam-001"));
        }
    }

    @Nested
    @DisplayName("PUT /camera/{id} - 更新摄像头")
    class Update {

        @Test
        @DisplayName("更新成功")
        void update_valid_success() throws Exception {
            CameraUpdateRequest req = new CameraUpdateRequest();
            req.setName("新名称");

            mockMvc.perform(put("/camera/cam-001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(cameraService).updateCamera(eq("cam-001"), any(CameraUpdateRequest.class));
        }
    }

    @Nested
    @DisplayName("DELETE /camera/{id} - 删除摄像头")
    class Delete {

        @Test
        @DisplayName("删除成功")
        void delete_success() throws Exception {
            mockMvc.perform(delete("/camera/cam-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(cameraService).deleteCamera("cam-001");
        }
    }

    @Nested
    @DisplayName("GET /camera/{id}/status - 摄像头状态")
    class Status {

        @Test
        @DisplayName("查询状态返回成功")
        void status_returnsVO() throws Exception {
            CameraStatusVO vo = new CameraStatusVO();
            when(cameraService.getCameraStatus("cam-001")).thenReturn(vo);

            mockMvc.perform(get("/camera/cam-001/status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }
    }

    @Nested
    @DisplayName("POST /camera/{cameraId}/detect - 检测")
    class Detect {

        @Test
        @DisplayName("检测调用成功")
        void detect_callsService() throws Exception {
            CameraDetectResponse resp = new CameraDetectResponse();
            when(cameraDetectService.detect(eq("cam-001"), any(CameraDetectRequest.class))).thenReturn(resp);

            mockMvc.perform(post("/camera/cam-001/detect")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }
    }

    @Nested
    @DisplayName("POST /camera/{id}/reconnect - 重连")
    class Reconnect {

        @Test
        @DisplayName("重连调用成功")
        void reconnect_success() throws Exception {
            mockMvc.perform(post("/camera/cam-001/reconnect"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(cameraService).reconnect("cam-001");
        }
    }
}
