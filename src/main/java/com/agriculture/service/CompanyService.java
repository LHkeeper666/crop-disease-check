package com.agriculture.service;

import com.agriculture.dto.JoinCompanyDTO;
import com.agriculture.dto.ValidateInviteDTO;
import com.agriculture.vo.CompanyInfoVO;
import com.agriculture.vo.CompanyMemberVO;
import com.agriculture.vo.JoinCompanyVO;
import com.agriculture.vo.ValidateInviteVO;
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
