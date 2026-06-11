package com.agriculture.controller;

import com.agriculture.dto.JoinCompanyDTO;
import com.agriculture.dto.ValidateInviteDTO;
import com.agriculture.exception.BusinessException;
import com.agriculture.exception.GlobalExceptionHandler;
import com.agriculture.service.CompanyService;
import com.agriculture.vo.*;
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 企业/租户模块控制器测试
 */
@ExtendWith(MockitoExtension.class)
class CompanyControllerTest {

    private MockMvc mockMvc;

    @Mock
    private CompanyService companyService;

    @InjectMocks
    private CompanyController companyController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(companyController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new LocalValidatorFactoryBean())
                .build();
    }

    // ==================== 4.1 验证邀请码接口 ====================

    @Nested
    @DisplayName("4.1 验证邀请码接口")
    class ValidateInviteCode {

        @Test
        @DisplayName("验证有效的邀请码")
        void validate_validCode_returnsCompanyName() throws Exception {
            ValidateInviteDTO dto = new ValidateInviteDTO();
            dto.setInviteCode("TF2026");

            ValidateInviteVO vo = new ValidateInviteVO();
            vo.setValid(true);
            vo.setCompanyName("TreeForge");

            when(companyService.validateInviteCode(any(ValidateInviteDTO.class)))
                    .thenReturn(vo);

            mockMvc.perform(post("/api/company/validate-invite")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.valid").value(true))
                    .andExpect(jsonPath("$.data.companyName").value("TreeForge"));
        }

        @Test
        @DisplayName("邀请码为空时返回400")
        void validate_emptyCode_returns400() throws Exception {
            ValidateInviteDTO dto = new ValidateInviteDTO();
            dto.setInviteCode("");

            mockMvc.perform(post("/api/company/validate-invite")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("邀请码不能为空"));
        }

        @Test
        @DisplayName("邀请码不存在时返回40090")
        void validate_invalidCode_returns40090() throws Exception {
            ValidateInviteDTO dto = new ValidateInviteDTO();
            dto.setInviteCode("INVALID");

            when(companyService.validateInviteCode(any(ValidateInviteDTO.class)))
                    .thenThrow(new BusinessException(40090, "邀请码不存在"));

            mockMvc.perform(post("/api/company/validate-invite")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40090))
                    .andExpect(jsonPath("$.message").value("邀请码不存在"));
        }

        @Test
        @DisplayName("邀请码已过期时返回40091")
        void validate_expiredCode_returns40091() throws Exception {
            ValidateInviteDTO dto = new ValidateInviteDTO();
            dto.setInviteCode("EXPIRED");

            when(companyService.validateInviteCode(any(ValidateInviteDTO.class)))
                    .thenThrow(new BusinessException(40091, "邀请码已过期"));

            mockMvc.perform(post("/api/company/validate-invite")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40091))
                    .andExpect(jsonPath("$.message").value("邀请码已过期"));
        }
    }

    // ==================== 4.2 通过邀请码加入企业接口 ====================

    @Nested
    @DisplayName("4.2 通过邀请码加入企业接口")
    class JoinCompany {

        @Test
        @DisplayName("成功加入企业")
        void join_validCode_success() throws Exception {
            JoinCompanyDTO dto = new JoinCompanyDTO();
            dto.setInviteCode("TF2026");

            JoinCompanyVO vo = new JoinCompanyVO();
            vo.setCompanyId("company-001");
            vo.setCompanyName("TreeForge");

            when(companyService.joinCompany(any(JoinCompanyDTO.class), anyString()))
                    .thenReturn(vo);

            mockMvc.perform(post("/api/company/join")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto))
                            .requestAttr("userId", "user-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.companyId").value("company-001"))
                    .andExpect(jsonPath("$.data.companyName").value("TreeForge"))
                    .andExpect(jsonPath("$.message").value("已成功加入 TreeForge"));
        }

        @Test
        @DisplayName("用户已加入企业时返回40092")
        void join_alreadyJoined_returns40092() throws Exception {
            JoinCompanyDTO dto = new JoinCompanyDTO();
            dto.setInviteCode("TF2026");

            when(companyService.joinCompany(any(JoinCompanyDTO.class), anyString()))
                    .thenThrow(new BusinessException(40092, "用户已加入企业，请勿重复加入"));

            mockMvc.perform(post("/api/company/join")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto))
                            .requestAttr("userId", "user-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40092))
                    .andExpect(jsonPath("$.message").value("用户已加入企业，请勿重复加入"));
        }

        @Test
        @DisplayName("企业成员已达上限时返回40093")
        void join_fullMember_returns40093() throws Exception {
            JoinCompanyDTO dto = new JoinCompanyDTO();
            dto.setInviteCode("TF2026");

            when(companyService.joinCompany(any(JoinCompanyDTO.class), anyString()))
                    .thenThrow(new BusinessException(40093, "该企业成员数量已达上限"));

            mockMvc.perform(post("/api/company/join")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto))
                            .requestAttr("userId", "user-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40093))
                    .andExpect(jsonPath("$.message").value("该企业成员数量已达上限"));
        }
    }

    // ==================== 4.3 获取企业信息接口 ====================

    @Nested
    @DisplayName("4.3 获取企业信息接口")
    class GetCompanyInfo {

        @Test
        @DisplayName("获取企业信息成功")
        void getInfo_success() throws Exception {
            CompanyInfoVO vo = new CompanyInfoVO();
            vo.setId("company-001");
            vo.setName("TreeForge");
            vo.setInviteCode("TF2026");
            vo.setMemberCount(12);
            vo.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0, 0));

            when(companyService.getCompanyInfo(anyString()))
                    .thenReturn(vo);

            mockMvc.perform(get("/api/company/info")
                            .requestAttr("userId", "user-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.id").value("company-001"))
                    .andExpect(jsonPath("$.data.name").value("TreeForge"))
                    .andExpect(jsonPath("$.data.inviteCode").value("TF2026"))
                    .andExpect(jsonPath("$.data.memberCount").value(12));
        }

        @Test
        @DisplayName("用户未加入企业时返回错误")
        void getInfo_noCompany_returnsError() throws Exception {
            when(companyService.getCompanyInfo(anyString()))
                    .thenThrow(new BusinessException("用户尚未加入任何企业"));

            mockMvc.perform(get("/api/company/info")
                            .requestAttr("userId", "user-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.message").value("用户尚未加入任何企业"));
        }
    }

    // ==================== 4.4 企业成员列表接口 ====================

    @Nested
    @DisplayName("4.4 企业成员列表接口")
    class ListMembers {

        @Test
        @DisplayName("获取成员列表成功")
        void listMembers_success() throws Exception {
            CompanyMemberVO member1 = new CompanyMemberVO();
            member1.setId("user-001");
            member1.setUsername("zhangsan");
            member1.setName("张三");
            member1.setRole("MANAGER");
            member1.setApproved(true);
            member1.setJoinedAt(LocalDateTime.of(2026, 3, 1, 10, 0, 0));

            CompanyMemberVO member2 = new CompanyMemberVO();
            member2.setId("user-002");
            member2.setUsername("lisi");
            member2.setName("李四");
            member2.setRole("EXPERT");
            member2.setApproved(true);
            member2.setJoinedAt(LocalDateTime.of(2026, 4, 1, 10, 0, 0));

            when(companyService.listMembers(anyString()))
                    .thenReturn(List.of(member1, member2));

            mockMvc.perform(get("/api/company/members")
                            .requestAttr("userId", "user-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].username").value("zhangsan"))
                    .andExpect(jsonPath("$.data[0].role").value("MANAGER"))
                    .andExpect(jsonPath("$.data[1].username").value("lisi"));
        }
    }

    // ==================== 4.5 刷新邀请码接口 ====================

    @Nested
    @DisplayName("4.5 刷新邀请码接口")
    class RefreshInviteCode {

        @Test
        @DisplayName("刷新邀请码成功")
        void refresh_success() throws Exception {
            when(companyService.refreshInviteCode(anyString()))
                    .thenReturn("NEWCODE");

            mockMvc.perform(post("/api/company/refresh-invite")
                            .requestAttr("userId", "user-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value("NEWCODE"))
                    .andExpect(jsonPath("$.message").value("邀请码已刷新，旧邀请码已失效"));
        }

        @Test
        @DisplayName("用户未加入企业时返回错误")
        void refresh_noCompany_returnsError() throws Exception {
            when(companyService.refreshInviteCode(anyString()))
                    .thenThrow(new BusinessException("用户尚未加入任何企业"));

            mockMvc.perform(post("/api/company/refresh-invite")
                            .requestAttr("userId", "user-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.message").value("用户尚未加入任何企业"));
        }
    }
}
