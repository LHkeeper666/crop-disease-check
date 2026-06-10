package com.agriculture.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.agriculture.dao.mapper.SysUserMapper;
import com.agriculture.dto.AdminUpdateUserDTO;
import com.agriculture.dto.UpdateUserDTO;
import com.agriculture.dto.UserQueryDTO;
import com.agriculture.entity.SysUser;
import com.agriculture.exception.BusinessException;
import com.agriculture.service.UserService;
import com.agriculture.vo.PageResult;
import com.agriculture.vo.UserSimpleVO;
import com.agriculture.vo.UserVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户服务实现类
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final SysUserMapper userMapper;

    @Override
    public UserVO getCurrentUser(String userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        UserVO vo = new UserVO();
        BeanUtil.copyProperties(user, vo);
        return vo;
    }

    @Override
    @Transactional
    public UserVO updateCurrentUser(String userId, UpdateUserDTO dto) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        // 更新字段
        if (StringUtils.hasText(dto.getName())) {
            user.setName(dto.getName());
        }
        if (StringUtils.hasText(dto.getPhone())) {
            user.setPhone(dto.getPhone());
        }
        if (StringUtils.hasText(dto.getEmail())) {
            user.setEmail(dto.getEmail());
        }
        user.setUpdatedAt(LocalDateTime.now());

        userMapper.updateById(user);

        UserVO vo = new UserVO();
        BeanUtil.copyProperties(user, vo);
        return vo;
    }

    @Override
    public PageResult<UserSimpleVO> getUserList(UserQueryDTO dto) {
        // 构建查询条件
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getDeleted, 0);

        // 关键词搜索
        if (StringUtils.hasText(dto.getKeyword())) {
            wrapper.and(w -> w
                    .like(SysUser::getUsername, dto.getKeyword())
                    .or().like(SysUser::getName, dto.getKeyword())
                    .or().like(SysUser::getPhone, dto.getKeyword())
            );
        }

        // 角色筛选
        if (StringUtils.hasText(dto.getRole())) {
            wrapper.eq(SysUser::getRole, dto.getRole());
        }

        // 状态筛选
        if (StringUtils.hasText(dto.getStatus())) {
            wrapper.eq(SysUser::getStatus, dto.getStatus());
        }

        wrapper.orderByDesc(SysUser::getCreatedAt);

        // 分页查询
        Page<SysUser> page = new Page<>(dto.getPage(), dto.getSize());
        Page<SysUser> result = userMapper.selectPage(page, wrapper);

        // 转换为VO
        List<UserSimpleVO> records = result.getRecords().stream()
                .map(user -> {
                    UserSimpleVO vo = new UserSimpleVO();
                    BeanUtil.copyProperties(user, vo);
                    return vo;
                })
                .collect(Collectors.toList());

        return new PageResult<>(records, result.getTotal(), result.getSize(),
                result.getCurrent(), result.getPages());
    }

    @Override
    public UserVO getUserById(String id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        UserVO vo = new UserVO();
        BeanUtil.copyProperties(user, vo);
        return vo;
    }

    @Override
    @Transactional
    public UserVO updateUser(String id, AdminUpdateUserDTO dto) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        // 更新字段
        if (StringUtils.hasText(dto.getName())) {
            user.setName(dto.getName());
        }
        if (StringUtils.hasText(dto.getPhone())) {
            user.setPhone(dto.getPhone());
        }
        if (StringUtils.hasText(dto.getEmail())) {
            user.setEmail(dto.getEmail());
        }
        if (StringUtils.hasText(dto.getRole())) {
            user.setRole(dto.getRole());
        }
        if (StringUtils.hasText(dto.getStatus())) {
            user.setStatus(dto.getStatus());
        }
        user.setUpdatedAt(LocalDateTime.now());

        userMapper.updateById(user);

        UserVO vo = new UserVO();
        BeanUtil.copyProperties(user, vo);
        return vo;
    }

    @Override
    @Transactional
    public void deleteUser(String id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        // 逻辑删除
        user.setDeleted((byte) 1);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }
}
