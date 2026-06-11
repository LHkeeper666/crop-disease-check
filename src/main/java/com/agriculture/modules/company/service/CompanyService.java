package com.agriculture.modules.company.service;

import com.agriculture.modules.company.dto.JoinCompanyDTO;
import com.agriculture.modules.company.dto.ValidateInviteDTO;
import com.agriculture.modules.company.vo.CompanyInfoVO;
import com.agriculture.modules.company.vo.CompanyMemberVO;
import com.agriculture.modules.company.vo.JoinCompanyVO;
import com.agriculture.modules.company.vo.ValidateInviteVO;
import java.util.List;

/**
 * 企业服务接口
 */
public interface CompanyService {

    /**
     * 验证邀请码是否有效
     */
    ValidateInviteVO validateInviteCode(ValidateInviteDTO dto);

    /**
     * 通过邀请码加入企业
     */
    JoinCompanyVO joinCompany(JoinCompanyDTO dto, String userId);

    /**
     * 获取当前用户所属企业信息
     */
    CompanyInfoVO getCompanyInfo(String userId);

    /**
     * 获取企业成员列表（管理员）
     */
    List<CompanyMemberVO> listMembers(String userId);

    /**
     * 刷新邀请码（管理员）
     */
    String refreshInviteCode(String userId);
}
