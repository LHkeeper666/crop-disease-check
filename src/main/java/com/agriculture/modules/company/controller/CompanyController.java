package com.agriculture.modules.company.controller;

import com.agriculture.common.annotation.RequireRole;
import com.agriculture.modules.company.dto.JoinCompanyDTO;
import com.agriculture.modules.company.dto.ValidateInviteDTO;
import com.agriculture.modules.company.service.CompanyService;
import com.agriculture.modules.company.vo.CompanyInfoVO;
import com.agriculture.modules.company.vo.CompanyMemberVO;
import com.agriculture.modules.company.vo.JoinCompanyVO;
import com.agriculture.common.vo.Result;
import com.agriculture.modules.company.vo.ValidateInviteVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 企业/租户控制器
 */
@RestController
@RequestMapping("/api/company")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService companyService;

    /**
     * 验证邀请码（公开接口）
     */
    @PostMapping("/validate-invite")
    public Result<ValidateInviteVO> validateInviteCode(@Valid @RequestBody ValidateInviteDTO dto) {
        ValidateInviteVO vo = companyService.validateInviteCode(dto);
        return Result.success(vo);
    }

    /**
     * 通过邀请码加入企业
     */
    @PostMapping("/join")
    public Result<JoinCompanyVO> joinCompany(@Valid @RequestBody JoinCompanyDTO dto,
                                             HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        JoinCompanyVO vo = companyService.joinCompany(dto, userId);
        return Result.success("已成功加入 " + vo.getCompanyName(), vo);
    }

    /**
     * 获取当前用户所属企业信息
     */
    @GetMapping("/info")
    public Result<CompanyInfoVO> getCompanyInfo(HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        CompanyInfoVO vo = companyService.getCompanyInfo(userId);
        return Result.success(vo);
    }

    /**
     * 企业成员列表（管理员）
     */
    @GetMapping("/members")
    @RequireRole("ADMIN")
    public Result<List<CompanyMemberVO>> listMembers(HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        List<CompanyMemberVO> voList = companyService.listMembers(userId);
        return Result.success(voList);
    }

    /**
     * 刷新邀请码（管理员）
     */
    @PostMapping("/refresh-invite")
    @RequireRole("ADMIN")
    public Result<String> refreshInviteCode(HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        String newInviteCode = companyService.refreshInviteCode(userId);
        return Result.success("邀请码已刷新，旧邀请码已失效", newInviteCode);
    }
}
