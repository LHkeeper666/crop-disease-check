package com.agriculture.controller;

import com.agriculture.modules.inference.controller.InferenceController;
import com.agriculture.modules.inference.service.InferenceService;
import com.agriculture.modules.pestDiseaseInfo.dto.AuditDTO;
import com.agriculture.modules.pestDiseaseInfo.dto.PreventionPlanDTO;
import com.agriculture.modules.pestDiseaseInfo.vo.PendingAuditVO;
import com.agriculture.modules.pestDiseaseInfo.vo.PendingReviewVO;
import com.agriculture.modules.pestDiseaseInfo.vo.PreventionPlanVO;
import com.agriculture.modules.pestDiseaseInfo.vo.PreventionPlanVersionVO;
import com.agriculture.common.exception.BusinessException;
import com.agriculture.common.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 病虫害识别模块控制器测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InferenceController 病虫害识别控制器测试")
class InferenceControllerTest {

    private MockMvc mockMvc;

    @Mock
    private InferenceService inferenceService;

    @InjectMocks
    private InferenceController inferenceController;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @BeforeEach
    void setUp() {
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setObjectMapper(objectMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(inferenceController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new LocalValidatorFactoryBean())
                .setMessageConverters(converter)
                .build();
    }

    // ==================== 辅助方法 ====================

    private PendingReviewVO createPendingReview(String id, String reportId) {
        PendingReviewVO vo = new PendingReviewVO();
        vo.setId(id);
        vo.setReportId(reportId);
        vo.setImageUrl("http://example.com/img/" + id + ".jpg");
        vo.setPestName("稻飞虱");
        vo.setConfidence(new BigDecimal("0.45"));
        vo.setReporterName("张三");
        vo.setGridLabel("A1");
        vo.setFoundAt(LocalDateTime.of(2026, 6, 10, 8, 30, 0));
        vo.setCreatedAt(LocalDateTime.of(2026, 6, 10, 9, 0, 0));
        return vo;
    }

    private PendingAuditVO createPendingAudit(String id, String reportId) {
        PendingAuditVO vo = new PendingAuditVO();
        vo.setId(id);
        vo.setReportId(reportId);
        vo.setImageUrl("http://example.com/img/" + id + ".jpg");
        vo.setPestName("稻瘟病");
        vo.setConfidence(new BigDecimal("0.92"));
        vo.setReporterName("李四");
        vo.setGridLabel("B2");
        vo.setCropType("水稻");
        vo.setFoundAt(LocalDateTime.of(2026, 6, 11, 10, 0, 0));
        return vo;
    }

    private PreventionPlanVO createPreventionPlanVO() {
        PreventionPlanVO vo = new PreventionPlanVO();
        vo.setId("plan-001");
        vo.setContent("建议使用吡虫啉进行防治，每亩用量30g");
        vo.setSuggestTime(LocalDate.of(2026, 6, 15));
        vo.setAuthorName("王专家");
        vo.setVersion(2);
        vo.setCreatedAt(LocalDateTime.of(2026, 6, 12, 14, 0, 0));

        PreventionPlanVersionVO v1 = new PreventionPlanVersionVO();
        v1.setId(1L);
        v1.setContent("初版方案");
        v1.setSuggestTime(LocalDate.of(2026, 6, 14));
        v1.setVersion(1);
        v1.setCreatedAt(LocalDateTime.of(2026, 6, 11, 10, 0, 0));

        PreventionPlanVersionVO v2 = new PreventionPlanVersionVO();
        v2.setId(2L);
        v2.setContent("建议使用吡虫啉进行防治，每亩用量30g");
        v2.setSuggestTime(LocalDate.of(2026, 6, 15));
        v2.setVersion(2);
        v2.setCreatedAt(LocalDateTime.of(2026, 6, 12, 14, 0, 0));

        vo.setVersions(List.of(v1, v2));
        return vo;
    }

    // ==================== 7.1 待复核列表 ====================

    @Nested
    @DisplayName("7.1 待复核列表接口")
    class ListPendingReview {

        @Test
        @DisplayName("获取待复核列表成功")
        void listPendingReview_success() throws Exception {
            PendingReviewVO vo = createPendingReview("r-001", "report-001");
            Page<PendingReviewVO> page = new Page<>(1, 20);
            page.setRecords(List.of(vo));
            page.setTotal(1);

            when(inferenceService.listPendingReview(isNull(), eq(1), eq(20)))
                    .thenReturn(page);

            mockMvc.perform(get("/inference/pending-review"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.records").isArray())
                    .andExpect(jsonPath("$.data.records.length()").value(1))
                    .andExpect(jsonPath("$.data.records[0].id").value("r-001"))
                    .andExpect(jsonPath("$.data.records[0].reportId").value("report-001"))
                    .andExpect(jsonPath("$.data.records[0].pestName").value("稻飞虱"))
                    .andExpect(jsonPath("$.data.records[0].confidence").value(0.45))
                    .andExpect(jsonPath("$.data.records[0].reporterName").value("张三"))
                    .andExpect(jsonPath("$.data.records[0].gridLabel").value("A1"))
                    .andExpect(jsonPath("$.data.total").value(1));
        }

        @Test
        @DisplayName("按置信度排序获取待复核列表")
        void listPendingReview_sortByConfidence() throws Exception {
            Page<PendingReviewVO> page = new Page<>(1, 20);
            page.setRecords(List.of());
            page.setTotal(0);

            when(inferenceService.listPendingReview(eq("desc"), eq(1), eq(20)))
                    .thenReturn(page);

            mockMvc.perform(get("/inference/pending-review")
                            .param("sortByConfidence", "desc"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.records").isEmpty());
        }

        @Test
        @DisplayName("分页参数传递正确")
        void listPendingReview_pagination() throws Exception {
            Page<PendingReviewVO> page = new Page<>(2, 10);
            page.setRecords(List.of());
            page.setTotal(25);

            when(inferenceService.listPendingReview(isNull(), eq(2), eq(10)))
                    .thenReturn(page);

            mockMvc.perform(get("/inference/pending-review")
                            .param("page", "2")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.current").value(2))
                    .andExpect(jsonPath("$.data.size").value(10))
                    .andExpect(jsonPath("$.data.total").value(25));
        }
    }

    // ==================== 7.2 待审核列表 ====================

    @Nested
    @DisplayName("7.2 待审核列表接口")
    class ListPendingAudit {

        @Test
        @DisplayName("获取待审核列表成功")
        void listPendingAudit_success() throws Exception {
            PendingAuditVO vo = createPendingAudit("a-001", "report-002");
            Page<PendingAuditVO> page = new Page<>(1, 20);
            page.setRecords(List.of(vo));
            page.setTotal(1);

            when(inferenceService.listPendingAudit(eq(1), eq(20)))
                    .thenReturn(page);

            mockMvc.perform(get("/inference/pending-audit"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.records").isArray())
                    .andExpect(jsonPath("$.data.records.length()").value(1))
                    .andExpect(jsonPath("$.data.records[0].id").value("a-001"))
                    .andExpect(jsonPath("$.data.records[0].reportId").value("report-002"))
                    .andExpect(jsonPath("$.data.records[0].pestName").value("稻瘟病"))
                    .andExpect(jsonPath("$.data.records[0].confidence").value(0.92))
                    .andExpect(jsonPath("$.data.records[0].cropType").value("水稻"))
                    .andExpect(jsonPath("$.data.total").value(1));
        }

        @Test
        @DisplayName("待审核列表为空")
        void listPendingAudit_empty() throws Exception {
            Page<PendingAuditVO> page = new Page<>(1, 20);
            page.setRecords(List.of());
            page.setTotal(0);

            when(inferenceService.listPendingAudit(eq(1), eq(20)))
                    .thenReturn(page);

            mockMvc.perform(get("/inference/pending-audit"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.records").isEmpty())
                    .andExpect(jsonPath("$.data.total").value(0));
        }

        @Test
        @DisplayName("分页参数传递正确")
        void listPendingAudit_pagination() throws Exception {
            Page<PendingAuditVO> page = new Page<>(3, 5);
            page.setRecords(List.of());
            page.setTotal(50);

            when(inferenceService.listPendingAudit(eq(3), eq(5)))
                    .thenReturn(page);

            mockMvc.perform(get("/inference/pending-audit")
                            .param("page", "3")
                            .param("size", "5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.current").value(3))
                    .andExpect(jsonPath("$.data.size").value(5))
                    .andExpect(jsonPath("$.data.total").value(50));
        }
    }

    // ==================== 7.3 审核上报 ====================

    @Nested
    @DisplayName("7.3 审核上报接口")
    class AuditReport {

        @Test
        @DisplayName("审核通过成功")
        void audit_approve_success() throws Exception {
            AuditDTO dto = new AuditDTO();
            dto.setAction("approve");

            doNothing().when(inferenceService).auditReport(eq("report-001"), any(AuditDTO.class), eq("user-001"));

            mockMvc.perform(post("/inference/report-001/audit")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto))
                            .requestAttr("userId", "user-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("审核成功"));
        }

        @Test
        @DisplayName("审核驳回成功（附带评论）")
        void audit_reject_withComment_success() throws Exception {
            AuditDTO dto = new AuditDTO();
            dto.setAction("reject");
            dto.setComment("图片不清晰，需要重新拍摄");

            doNothing().when(inferenceService).auditReport(eq("report-001"), any(AuditDTO.class), eq("user-001"));

            mockMvc.perform(post("/inference/report-001/audit")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto))
                            .requestAttr("userId", "user-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("审核成功"));
        }

        @Test
        @DisplayName("操作类型为空时返回400")
        void audit_emptyAction_returns400() throws Exception {
            AuditDTO dto = new AuditDTO();
            dto.setAction("");

            mockMvc.perform(post("/inference/report-001/audit")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto))
                            .requestAttr("userId", "user-001"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("操作类型不能为空"));
        }

        @Test
        @DisplayName("记录已审核时返回40052")
        void audit_alreadyAudited_returns40052() throws Exception {
            AuditDTO dto = new AuditDTO();
            dto.setAction("approve");

            doThrow(new BusinessException(40052, "该记录已审核，请勿重复操作"))
                    .when(inferenceService).auditReport(eq("report-done"), any(AuditDTO.class), eq("user-001"));

            mockMvc.perform(post("/inference/report-done/audit")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto))
                            .requestAttr("userId", "user-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40052))
                    .andExpect(jsonPath("$.message").value("该记录已审核，请勿重复操作"));
        }

        @Test
        @DisplayName("驳回理由为空时返回40050")
        void audit_rejectEmptyReason_returns40050() throws Exception {
            AuditDTO dto = new AuditDTO();
            dto.setAction("reject");

            doThrow(new BusinessException(40050, "驳回理由不能为空"))
                    .when(inferenceService).auditReport(eq("report-001"), any(AuditDTO.class), eq("user-001"));

            mockMvc.perform(post("/inference/report-001/audit")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto))
                            .requestAttr("userId", "user-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40050))
                    .andExpect(jsonPath("$.message").value("驳回理由不能为空"));
        }

        @Test
        @DisplayName("驳回理由不足10字时返回40051")
        void audit_rejectShortReason_returns40051() throws Exception {
            AuditDTO dto = new AuditDTO();
            dto.setAction("reject");
            dto.setComment("太短");

            doThrow(new BusinessException(40051, "驳回理由不能少于10个字"))
                    .when(inferenceService).auditReport(eq("report-001"), any(AuditDTO.class), eq("user-001"));

            mockMvc.perform(post("/inference/report-001/audit")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto))
                            .requestAttr("userId", "user-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40051))
                    .andExpect(jsonPath("$.message").value("驳回理由不能少于10个字"));
        }

        @Test
        @DisplayName("记录不存在时返回404")
        void audit_notFound_returns404() throws Exception {
            AuditDTO dto = new AuditDTO();
            dto.setAction("approve");

            doThrow(new BusinessException(404, "上报记录不存在"))
                    .when(inferenceService).auditReport(eq("not-exist"), any(AuditDTO.class), eq("user-001"));

            mockMvc.perform(post("/inference/not-exist/audit")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto))
                            .requestAttr("userId", "user-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.message").value("上报记录不存在"));
        }
    }

    // ==================== 7.4 制定防治方案 ====================

    @Nested
    @DisplayName("7.4 制定防治方案接口")
    class CreatePreventionPlan {

        @Test
        @DisplayName("制定防治方案成功")
        void createPlan_success() throws Exception {
            PreventionPlanDTO dto = new PreventionPlanDTO();
            dto.setContent("使用吡虫啉喷洒防治");
            dto.setSuggestTime(LocalDate.of(2026, 6, 20));

            doNothing().when(inferenceService).createPreventionPlan(eq("report-001"), any(PreventionPlanDTO.class), eq("user-001"));

            mockMvc.perform(post("/inference/report-001/prevention-plan")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto))
                            .requestAttr("userId", "user-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("防治方案制定成功"));
        }

        @Test
        @DisplayName("方案内容为空时返回400")
        void createPlan_emptyContent_returns400() throws Exception {
            PreventionPlanDTO dto = new PreventionPlanDTO();
            dto.setContent("");

            mockMvc.perform(post("/inference/report-001/prevention-plan")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto))
                            .requestAttr("userId", "user-001"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("方案内容不能为空"));
        }

        @Test
        @DisplayName("记录不存在时返回404")
        void createPlan_notFound_returns404() throws Exception {
            PreventionPlanDTO dto = new PreventionPlanDTO();
            dto.setContent("防治方案内容");

            doThrow(new BusinessException(404, "上报记录不存在"))
                    .when(inferenceService).createPreventionPlan(eq("not-exist"), any(PreventionPlanDTO.class), eq("user-001"));

            mockMvc.perform(post("/inference/not-exist/prevention-plan")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto))
                            .requestAttr("userId", "user-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.message").value("上报记录不存在"));
        }
    }

    // ==================== 7.5 修改防治方案 ====================

    @Nested
    @DisplayName("7.5 修改防治方案接口")
    class UpdatePreventionPlan {

        @Test
        @DisplayName("修改防治方案成功")
        void updatePlan_success() throws Exception {
            PreventionPlanDTO dto = new PreventionPlanDTO();
            dto.setContent("更新后的防治方案");
            dto.setSuggestTime(LocalDate.of(2026, 6, 22));

            doNothing().when(inferenceService).updatePreventionPlan(eq("report-001"), any(PreventionPlanDTO.class), eq("user-001"));

            mockMvc.perform(put("/inference/report-001/prevention-plan")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto))
                            .requestAttr("userId", "user-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("防治方案修改成功"));
        }

        @Test
        @DisplayName("方案内容为空时返回400")
        void updatePlan_emptyContent_returns400() throws Exception {
            PreventionPlanDTO dto = new PreventionPlanDTO();
            dto.setContent("");

            mockMvc.perform(put("/inference/report-001/prevention-plan")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto))
                            .requestAttr("userId", "user-001"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("方案内容不能为空"));
        }

        @Test
        @DisplayName("方案不存在时返回404")
        void updatePlan_notFound_returns404() throws Exception {
            PreventionPlanDTO dto = new PreventionPlanDTO();
            dto.setContent("更新内容");

            doThrow(new BusinessException(404, "防治方案不存在"))
                    .when(inferenceService).updatePreventionPlan(eq("not-exist"), any(PreventionPlanDTO.class), eq("user-001"));

            mockMvc.perform(put("/inference/not-exist/prevention-plan")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto))
                            .requestAttr("userId", "user-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.message").value("防治方案不存在"));
        }
    }

    // ==================== 获取防治方案详情 ====================

    @Nested
    @DisplayName("获取防治方案详情接口")
    class GetPreventionPlan {

        @Test
        @DisplayName("获取防治方案详情成功")
        void getPlan_success() throws Exception {
            PreventionPlanVO vo = createPreventionPlanVO();

            when(inferenceService.getPreventionPlan("report-001")).thenReturn(vo);

            mockMvc.perform(get("/inference/report-001/prevention-plan"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.id").value("plan-001"))
                    .andExpect(jsonPath("$.data.content").value("建议使用吡虫啉进行防治，每亩用量30g"))
                    .andExpect(jsonPath("$.data.suggestTime").value("2026-06-15"))
                    .andExpect(jsonPath("$.data.authorName").value("王专家"))
                    .andExpect(jsonPath("$.data.version").value(2))
                    .andExpect(jsonPath("$.data.versions").isArray())
                    .andExpect(jsonPath("$.data.versions.length()").value(2))
                    .andExpect(jsonPath("$.data.versions[0].version").value(1))
                    .andExpect(jsonPath("$.data.versions[0].content").value("初版方案"))
                    .andExpect(jsonPath("$.data.versions[1].version").value(2));
        }

        @Test
        @DisplayName("方案不存在时返回404")
        void getPlan_notFound_returns404() throws Exception {
            when(inferenceService.getPreventionPlan("not-exist"))
                    .thenThrow(new BusinessException(404, "防治方案不存在"));

            mockMvc.perform(get("/inference/not-exist/prevention-plan"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.message").value("防治方案不存在"));
        }
    }
}
