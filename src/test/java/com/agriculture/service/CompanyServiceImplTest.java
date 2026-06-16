package com.agriculture.service;

import com.agriculture.common.exception.BusinessException;
import com.agriculture.modules.company.dto.JoinCompanyDTO;
import com.agriculture.modules.company.dto.ValidateInviteDTO;
import com.agriculture.modules.company.entity.Company;
import com.agriculture.modules.company.mapper.CompanyMapper;
import com.agriculture.modules.company.service.impl.CompanyServiceImpl;
import com.agriculture.modules.company.vo.CompanyInfoVO;
import com.agriculture.modules.company.vo.CompanyMemberVO;
import com.agriculture.modules.company.vo.JoinCompanyVO;
import com.agriculture.modules.company.vo.ValidateInviteVO;
import com.agriculture.modules.user.entity.SysUser;
import com.agriculture.modules.user.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CompanyServiceImpl 单元测试")
class CompanyServiceImplTest {

    @Mock private CompanyMapper companyMapper;
    @Mock private SysUserMapper userMapper;

    @InjectMocks
    private CompanyServiceImpl companyService;

    private Company sampleCompany;
    private SysUser sampleUser;

    @BeforeEach
    void setUp() {
        sampleCompany = new Company();
        sampleCompany.setId("company-001");
        sampleCompany.setName("TreeForge 智慧农场");
        sampleCompany.setInviteCode("TF2026");
        sampleCompany.setMemberLimit(50);
        sampleCompany.setCreatedAt(LocalDateTime.now());

        sampleUser = new SysUser();
        sampleUser.setId("u-001");
        sampleUser.setUsername("admin");
        sampleUser.setName("系统管理员");
        sampleUser.setRole("ADMIN");
        sampleUser.setCompanyId("company-001");
        sampleUser.setApproved((byte) 1);
        sampleUser.setDeleted((byte) 0);
        sampleUser.setCreatedAt(LocalDateTime.now());
    }

    // ==================== validateInviteCode ====================

    @Nested
    @DisplayName("validateInviteCode - 验证邀请码")
    class ValidateInviteCode {

        @Test
        @DisplayName("有效邀请码返回验证结果")
        void validate_validCode_returnsTrue() {
            when(companyMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(sampleCompany);

            ValidateInviteDTO dto = new ValidateInviteDTO();
            dto.setInviteCode("TF2026");

            ValidateInviteVO result = companyService.validateInviteCode(dto);

            assertTrue(result.getValid());
            assertEquals("TreeForge 智慧农场", result.getCompanyName());
        }

        @Test
        @DisplayName("不存在的邀请码抛异常")
        void validate_invalidCode_throwsException() {
            when(companyMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            ValidateInviteDTO dto = new ValidateInviteDTO();
            dto.setInviteCode("INVALID");

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> companyService.validateInviteCode(dto));
            assertEquals(40090, ex.getCode());
        }

        @Test
        @DisplayName("过期邀请码抛异常")
        void validate_expiredCode_throwsException() {
            sampleCompany.setExpireAt(LocalDateTime.now().minusDays(1));
            when(companyMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(sampleCompany);

            ValidateInviteDTO dto = new ValidateInviteDTO();
            dto.setInviteCode("TF2026");

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> companyService.validateInviteCode(dto));
            assertEquals(40091, ex.getCode());
        }
    }

    // ==================== joinCompany ====================

    @Nested
    @DisplayName("joinCompany - 加入企业")
    class JoinCompany {

        @Test
        @DisplayName("加入成功")
        void join_valid_success() {
            SysUser newUser = new SysUser();
            newUser.setId("u-002");
            newUser.setCompanyId("");
            newUser.setApproved((byte) 0);

            when(companyMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(sampleCompany);
            when(userMapper.selectById("u-002")).thenReturn(newUser);
            when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(10L);
            when(userMapper.updateById(any(SysUser.class))).thenReturn(1);

            JoinCompanyDTO dto = new JoinCompanyDTO();
            dto.setInviteCode("TF2026");

            JoinCompanyVO result = companyService.joinCompany(dto, "u-002");

            assertEquals("company-001", result.getCompanyId());
            assertEquals("TreeForge 智慧农场", result.getCompanyName());
        }

        @Test
        @DisplayName("用户已加入企业抛异常")
        void join_alreadyJoined_throwsException() {
            when(companyMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(sampleCompany);
            when(userMapper.selectById("u-001")).thenReturn(sampleUser);

            JoinCompanyDTO dto = new JoinCompanyDTO();
            dto.setInviteCode("TF2026");

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> companyService.joinCompany(dto, "u-001"));
            assertEquals(40092, ex.getCode());
        }

        @Test
        @DisplayName("企业成员已满抛异常")
        void join_atLimit_throwsException() {
            SysUser newUser = new SysUser();
            newUser.setId("u-002");
            newUser.setCompanyId("");

            when(companyMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(sampleCompany);
            when(userMapper.selectById("u-002")).thenReturn(newUser);
            when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(50L);

            JoinCompanyDTO dto = new JoinCompanyDTO();
            dto.setInviteCode("TF2026");

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> companyService.joinCompany(dto, "u-002"));
            assertEquals(40093, ex.getCode());
        }
    }

    // ==================== getCompanyInfo ====================

    @Nested
    @DisplayName("getCompanyInfo - 企业信息")
    class GetCompanyInfo {

        @Test
        @DisplayName("查询成功返回企业信息")
        void getCompanyInfo_exists_returnsInfo() {
            when(userMapper.selectById("u-001")).thenReturn(sampleUser);
            when(companyMapper.selectById("company-001")).thenReturn(sampleCompany);
            when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(15L);

            CompanyInfoVO result = companyService.getCompanyInfo("u-001");

            assertEquals("company-001", result.getId());
            assertEquals("TreeForge 智慧农场", result.getName());
            assertEquals(15, result.getMemberCount());
        }

        @Test
        @DisplayName("用户未加入企业抛异常")
        void getCompanyInfo_noCompany_throwsException() {
            SysUser noCompanyUser = new SysUser();
            noCompanyUser.setId("u-003");
            noCompanyUser.setCompanyId("");

            when(userMapper.selectById("u-003")).thenReturn(noCompanyUser);

            assertThrows(BusinessException.class,
                    () -> companyService.getCompanyInfo("u-003"));
        }
    }

    // ==================== listMembers ====================

    @Nested
    @DisplayName("listMembers - 成员列表")
    class ListMembers {

        @Test
        @DisplayName("返回企业成员列表")
        void listMembers_hasCompany_returnsMembers() {
            when(userMapper.selectById("u-001")).thenReturn(sampleUser);
            when(userMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(sampleUser));

            List<CompanyMemberVO> result = companyService.listMembers("u-001");

            assertEquals(1, result.size());
            assertEquals("admin", result.get(0).getUsername());
        }
    }

    // ==================== refreshInviteCode ====================

    @Nested
    @DisplayName("refreshInviteCode - 刷新邀请码")
    class RefreshInviteCode {

        @Test
        @DisplayName("刷新成功返回新邀请码")
        void refreshInviteCode_hasCompany_returnsNewCode() {
            when(userMapper.selectById("u-001")).thenReturn(sampleUser);
            when(companyMapper.selectById("company-001")).thenReturn(sampleCompany);
            when(companyMapper.updateById(any(Company.class))).thenReturn(1);

            String newCode = companyService.refreshInviteCode("u-001");

            assertNotNull(newCode);
            assertEquals(6, newCode.length());
            assertEquals(newCode, sampleCompany.getInviteCode());
        }
    }
}
