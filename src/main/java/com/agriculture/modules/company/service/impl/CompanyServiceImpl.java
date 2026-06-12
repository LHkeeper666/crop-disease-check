package com.agriculture.modules.company.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.agriculture.modules.company.mapper.CompanyMapper;
import com.agriculture.modules.user.mapper.SysUserMapper;
import com.agriculture.modules.company.dto.JoinCompanyDTO;
import com.agriculture.modules.company.dto.ValidateInviteDTO;
import com.agriculture.modules.company.entity.Company;
import com.agriculture.modules.user.entity.SysUser;
import com.agriculture.common.exception.BusinessException;
import com.agriculture.modules.company.service.CompanyService;
import com.agriculture.modules.company.vo.CompanyInfoVO;
import com.agriculture.modules.company.vo.CompanyMemberVO;
import com.agriculture.modules.company.vo.JoinCompanyVO;
import com.agriculture.modules.company.vo.ValidateInviteVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 企业服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyServiceImpl implements CompanyService {

    private final CompanyMapper companyMapper;
    private final SysUserMapper userMapper;

    @Override
    public ValidateInviteVO validateInviteCode(ValidateInviteDTO dto) {
        String inviteCode = dto.getInviteCode();

        // 查询企业
        LambdaQueryWrapper<Company> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Company::getInviteCode, inviteCode);
        Company company = companyMapper.selectOne(wrapper);

        if (company == null) {
            throw new BusinessException(40090, "邀请码不存在");
        }

        // 检查是否过期
        if (company.getExpireAt() != null && company.getExpireAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(40091, "邀请码已过期");
        }

        ValidateInviteVO vo = new ValidateInviteVO();
        vo.setValid(true);
        vo.setCompanyName(company.getName());
        return vo;
    }

    @Override
    @Transactional
    public JoinCompanyVO joinCompany(JoinCompanyDTO dto, String userId) {
        String inviteCode = dto.getInviteCode();

        // 查询企业
        LambdaQueryWrapper<Company> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Company::getInviteCode, inviteCode);
        Company company = companyMapper.selectOne(wrapper);

        if (company == null) {
            throw new BusinessException(40090, "邀请码不存在");
        }

        // 检查是否过期
        if (company.getExpireAt() != null && company.getExpireAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(40091, "邀请码已过期");
        }

        // 查询用户
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        // 检查用户是否已加入企业
        if (user.getCompanyId() != null && !user.getCompanyId().isEmpty()) {
            throw new BusinessException(40092, "用户已加入企业，请勿重复加入");
        }

        // 检查企业成员数量是否达到上限
        LambdaQueryWrapper<SysUser> countWrapper = new LambdaQueryWrapper<>();
        countWrapper.eq(SysUser::getCompanyId, company.getId());
        Long memberCount = userMapper.selectCount(countWrapper);

        if (memberCount >= company.getMemberLimit()) {
            throw new BusinessException(40093, "该企业成员数量已达上限");
        }

        // 更新用户的公司ID和审批状态
        user.setCompanyId(company.getId());
        user.setApproved((byte) 1);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);

        JoinCompanyVO vo = new JoinCompanyVO();
        vo.setCompanyId(company.getId());
        vo.setCompanyName(company.getName());
        return vo;
    }

    @Override
    public CompanyInfoVO getCompanyInfo(String userId) {
        // 查询用户
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        // 检查用户是否已加入企业
        if (user.getCompanyId() == null || user.getCompanyId().isEmpty()) {
            throw new BusinessException("用户尚未加入任何企业");
        }

        // 查询企业
        Company company = companyMapper.selectById(user.getCompanyId());
        if (company == null) {
            throw new BusinessException("企业不存在");
        }

        // 统计成员数量
        LambdaQueryWrapper<SysUser> countWrapper = new LambdaQueryWrapper<>();
        countWrapper.eq(SysUser::getCompanyId, company.getId());
        Long memberCount = userMapper.selectCount(countWrapper);

        CompanyInfoVO vo = new CompanyInfoVO();
        vo.setId(company.getId());
        vo.setName(company.getName());
        vo.setInviteCode(company.getInviteCode());
        vo.setMemberCount(memberCount.intValue());
        vo.setCreatedAt(company.getCreatedAt());
        return vo;
    }

    @Override
    public List<CompanyMemberVO> listMembers(String userId) {
        // 查询当前用户
        SysUser currentUser = userMapper.selectById(userId);
        if (currentUser == null) {
            throw new BusinessException("用户不存在");
        }

        // 检查用户是否已加入企业
        if (currentUser.getCompanyId() == null || currentUser.getCompanyId().isEmpty()) {
            throw new BusinessException("用户尚未加入任何企业");
        }

        // 查询企业成员
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getCompanyId, currentUser.getCompanyId());
        List<SysUser> members = userMapper.selectList(wrapper);

        return members.stream().map(member -> {
            CompanyMemberVO vo = new CompanyMemberVO();
            vo.setId(member.getId());
            vo.setUsername(member.getUsername());
            vo.setName(member.getName());
            vo.setRole(member.getRole());
            vo.setApproved(member.getApproved() == 1);
            vo.setJoinedAt(member.getCreatedAt());
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public String refreshInviteCode(String userId) {
        // 查询当前用户
        SysUser currentUser = userMapper.selectById(userId);
        if (currentUser == null) {
            throw new BusinessException("用户不存在");
        }

        // 检查用户是否已加入企业
        if (currentUser.getCompanyId() == null || currentUser.getCompanyId().isEmpty()) {
            throw new BusinessException("用户尚未加入任何企业");
        }

        // 查询企业
        Company company = companyMapper.selectById(currentUser.getCompanyId());
        if (company == null) {
            throw new BusinessException("企业不存在");
        }

        // 生成新邀请码
        String newInviteCode = RandomUtil.randomString(6).toUpperCase();
        company.setInviteCode(newInviteCode);
        company.setUpdatedAt(LocalDateTime.now());
        companyMapper.updateById(company);

        log.info("企业 {} 邀请码已刷新为: {}", company.getName(), newInviteCode);
        return newInviteCode;
    }
}
