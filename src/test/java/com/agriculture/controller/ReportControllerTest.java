package com.agriculture.controller;

import com.agriculture.common.exception.BusinessException;
import com.agriculture.common.exception.GlobalExceptionHandler;
import com.agriculture.modules.report.controller.ReportController;
import com.agriculture.modules.report.dto.ReportQueryDTO;
import com.agriculture.modules.report.dto.ReportUploadDTO;
import com.agriculture.modules.report.service.ReportService;
import com.agriculture.modules.report.vo.ReportDetailVO;
import com.agriculture.modules.report.vo.ReportListVO;
import com.agriculture.modules.report.vo.ReportUploadVO;
import com.agriculture.modules.user.entity.SysUser;
import com.agriculture.modules.user.mapper.SysUserMapper;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 图像上报模块控制器测试
 */
@ExtendWith(MockitoExtension.class)
class ReportControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ReportService reportService;

    @Mock
    private SysUserMapper sysUserMapper;

    @InjectMocks
    private ReportController reportController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        SysUser mockUser = new SysUser();
        mockUser.setId("user-001");
        mockUser.setCompanyId("company-001");
        lenient().when(sysUserMapper.selectById("user-001")).thenReturn(mockUser);

        mockMvc = MockMvcBuilders.standaloneSetup(reportController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new LocalValidatorFactoryBean())
                .build();
    }

    // ==================== 9.1 上传图片并上报接口 ====================

    @Nested
    @DisplayName("9.1 上传图片并上报接口")
    class UploadReport {

        @Test
        @DisplayName("上传成功")
        void upload_validFiles_success() throws Exception {
            ReportUploadVO vo = ReportUploadVO.builder()
                    .reportId("report-001")
                    .imageUrls(Arrays.asList("/images/report/20260609-uuid1.jpg", "/images/report/20260609-uuid2.jpg"))
                    .status("PENDING_RECOGNITION")
                    .build();

            when(reportService.uploadImages(any(), any(ReportUploadDTO.class), anyString(), anyString()))
                    .thenReturn(vo);

            MockMultipartFile image1 = new MockMultipartFile(
                    "images", "test1.jpg", "image/jpeg", "fake-image-data".getBytes());
            MockMultipartFile image2 = new MockMultipartFile(
                    "images", "test2.jpg", "image/jpeg", "fake-image-data".getBytes());

            mockMvc.perform(multipart("/report/upload")
                            .file(image1)
                            .file(image2)
                            .param("gridId", "grid-001")
                            .param("cropType", "番茄")
                            .param("foundAt", "2026-06-09T08:00:00")
                            .param("description", "叶片出现褐色斑点")
                            .requestAttr("userId", "user-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("上报成功，正在进行识别"))
                    .andExpect(jsonPath("$.data.reportId").value("report-001"))
                    .andExpect(jsonPath("$.data.imageUrls").isArray())
                    .andExpect(jsonPath("$.data.imageUrls.length()").value(2))
                    .andExpect(jsonPath("$.data.status").value("PENDING_RECOGNITION"));
        }

        @Test
        @DisplayName("文件格式不支持时返回40040")
        void upload_invalidFormat_returns40040() throws Exception {
            when(reportService.uploadImages(any(), any(ReportUploadDTO.class), anyString(), anyString()))
                    .thenThrow(new BusinessException(40040, "图片格式不支持（仅支持JPG/PNG）"));

            MockMultipartFile file = new MockMultipartFile(
                    "images", "test.gif", "image/gif", "fake-data".getBytes());

            mockMvc.perform(multipart("/report/upload")
                            .file(file)
                            .param("gridId", "grid-001")
                            .param("cropType", "番茄")
                            .param("foundAt", "2026-06-09T08:00:00")
                            .requestAttr("userId", "user-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40040))
                    .andExpect(jsonPath("$.message").value("图片格式不支持（仅支持JPG/PNG）"));
        }

        @Test
        @DisplayName("图片大小超过限制时返回40041")
        void upload_tooLarge_returns40041() throws Exception {
            when(reportService.uploadImages(any(), any(ReportUploadDTO.class), anyString(), anyString()))
                    .thenThrow(new BusinessException(40041, "图片大小超过10MB限制"));

            MockMultipartFile file = new MockMultipartFile(
                    "images", "test.jpg", "image/jpeg", "fake-data".getBytes());

            mockMvc.perform(multipart("/report/upload")
                            .file(file)
                            .param("gridId", "grid-001")
                            .param("cropType", "番茄")
                            .param("foundAt", "2026-06-09T08:00:00")
                            .requestAttr("userId", "user-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40041))
                    .andExpect(jsonPath("$.message").value("图片大小超过10MB限制"));
        }

        @Test
        @DisplayName("图片数量超过限制时返回40042")
        void upload_tooManyFiles_returns40042() throws Exception {
            when(reportService.uploadImages(any(), any(ReportUploadDTO.class), anyString(), anyString()))
                    .thenThrow(new BusinessException(40042, "单次上传图片数量超过10张"));

            MockMultipartFile file = new MockMultipartFile(
                    "images", "test.jpg", "image/jpeg", "fake-data".getBytes());

            mockMvc.perform(multipart("/report/upload")
                            .file(file)
                            .param("gridId", "grid-001")
                            .param("cropType", "番茄")
                            .param("foundAt", "2026-06-09T08:00:00")
                            .requestAttr("userId", "user-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40042))
                    .andExpect(jsonPath("$.message").value("单次上传图片数量超过10张"));
        }

        @Test
        @DisplayName("地块编号为空时返回400")
        void upload_emptyGridId_returns400() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "images", "test.jpg", "image/jpeg", "fake-data".getBytes());

            mockMvc.perform(multipart("/report/upload")
                            .file(file)
                            .param("gridId", "")
                            .param("cropType", "番茄")
                            .param("foundAt", "2026-06-09T08:00:00")
                            .requestAttr("userId", "user-001"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("地块编号不能为空"));
        }

        @Test
        @DisplayName("农作物品种为空时返回400")
        void upload_emptyCropType_returns400() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "images", "test.jpg", "image/jpeg", "fake-data".getBytes());

            mockMvc.perform(multipart("/report/upload")
                            .file(file)
                            .param("gridId", "grid-001")
                            .param("cropType", "")
                            .param("foundAt", "2026-06-09T08:00:00")
                            .requestAttr("userId", "user-001"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("农作物品种不能为空"));
        }
    }

    // ==================== 9.2 我的上报记录接口 ====================

    @Nested
    @DisplayName("9.2 我的上报记录接口")
    class MyReports {

        @Test
        @DisplayName("查询我的上报记录成功")
        void mine_defaultQuery_success() throws Exception {
            ReportListVO record1 = ReportListVO.builder()
                    .id("report-001")
                    .gridLabel("A1")
                    .cropType("番茄")
                    .imageUrls(Arrays.asList("/images/report/xxx.jpg"))
                    .foundAt(LocalDateTime.of(2026, 6, 9, 8, 0, 0))
                    .status("AUDITED")
                    .createdAt(LocalDateTime.of(2026, 6, 9, 8, 5, 0))
                    .build();

            Page<ReportListVO> page = new Page<>(1, 20, 1);
            page.setRecords(List.of(record1));

            when(reportService.getMyReports(any(ReportQueryDTO.class), anyString(), anyString()))
                    .thenReturn(page);

            mockMvc.perform(get("/report/mine")
                            .requestAttr("userId", "user-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.records").isArray())
                    .andExpect(jsonPath("$.data.records.length()").value(1))
                    .andExpect(jsonPath("$.data.records[0].id").value("report-001"))
                    .andExpect(jsonPath("$.data.records[0].gridLabel").value("A1"))
                    .andExpect(jsonPath("$.data.records[0].cropType").value("番茄"))
                    .andExpect(jsonPath("$.data.records[0].status").value("AUDITED"))
                    .andExpect(jsonPath("$.data.total").value(1));
        }

        @Test
        @DisplayName("按状态筛选")
        void mine_filterByStatus_success() throws Exception {
            Page<ReportListVO> page = new Page<>(1, 20, 0);
            page.setRecords(List.of());

            when(reportService.getMyReports(any(ReportQueryDTO.class), anyString(), anyString()))
                    .thenReturn(page);

            mockMvc.perform(get("/report/mine")
                            .param("status", "PENDING")
                            .requestAttr("userId", "user-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.records").isArray())
                    .andExpect(jsonPath("$.data.records.length()").value(0));
        }

        @Test
        @DisplayName("分页参数生效")
        void mine_withPagination_success() throws Exception {
            Page<ReportListVO> page = new Page<>(2, 10, 30);
            page.setRecords(List.of());

            when(reportService.getMyReports(any(ReportQueryDTO.class), anyString(), anyString()))
                    .thenReturn(page);

            mockMvc.perform(get("/report/mine")
                            .param("page", "2")
                            .param("size", "10")
                            .requestAttr("userId", "user-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.current").value(2))
                    .andExpect(jsonPath("$.data.size").value(10))
                    .andExpect(jsonPath("$.data.total").value(30));
        }
    }

    // ==================== 9.3 上报详情接口 ====================

    @Nested
    @DisplayName("9.3 上报详情接口")
    class ReportDetail {

        @Test
        @DisplayName("获取上报详情成功")
        void detail_validId_success() throws Exception {
            ReportDetailVO vo = ReportDetailVO.builder()
                    .id("report-001")
                    .reporterName("张三")
                    .gridLabel("A1")
                    .cropType("番茄")
                    .imageUrls(Arrays.asList("/images/report/xxx.jpg"))
                    .foundAt(LocalDateTime.of(2026, 6, 9, 8, 0, 0))
                    .description("叶片出现褐色斑点")
                    .status("AUDITED")
                    .createdAt(LocalDateTime.of(2026, 6, 9, 8, 5, 0))
                    .build();

            when(reportService.getReportDetail("report-001")).thenReturn(vo);

            mockMvc.perform(get("/report/report-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.id").value("report-001"))
                    .andExpect(jsonPath("$.data.reporterName").value("张三"))
                    .andExpect(jsonPath("$.data.gridLabel").value("A1"))
                    .andExpect(jsonPath("$.data.cropType").value("番茄"))
                    .andExpect(jsonPath("$.data.imageUrls").isArray())
                    .andExpect(jsonPath("$.data.description").value("叶片出现褐色斑点"))
                    .andExpect(jsonPath("$.data.status").value("AUDITED"));
        }

        @Test
        @DisplayName("上报记录不存在时返回错误")
        void detail_notFound_returnsError() throws Exception {
            when(reportService.getReportDetail("non-existent"))
                    .thenThrow(new BusinessException("上报记录不存在"));

            mockMvc.perform(get("/report/non-existent"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.message").value("上报记录不存在"));
        }
    }
}
