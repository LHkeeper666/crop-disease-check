package com.agriculture.modules.auth.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.agriculture.modules.user.mapper.SysUserMapper;
import com.agriculture.modules.user.dto.LoginByOtpDTO;
import com.agriculture.modules.user.dto.LoginDTO;
import com.agriculture.modules.user.dto.RegisterDTO;
import com.agriculture.modules.user.dto.SendOtpDTO;
import com.agriculture.modules.user.entity.SysUser;
import com.agriculture.common.exception.BusinessException;
import com.agriculture.modules.auth.service.AuthService;
import com.agriculture.common.service.EmailService;
import com.agriculture.common.util.JwtUtil;
import com.agriculture.modules.user.vo.LoginVO;
import com.agriculture.modules.user.vo.UserVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 认证服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final EmailService emailService;
    private final StringRedisTemplate redisTemplate;

    /**
     * 验证码Redis Key前缀
     */
    private static final String OTP_KEY_PREFIX = "otp:";
    /**
     * 验证码发送间隔Key前缀
     */
    private static final String OTP_INTERVAL_KEY_PREFIX = "otp_interval:";
    /**
     * 验证码有效期（分钟）
     */
    private static final int OTP_EXPIRE_MINUTES = 5;
    /**
     * 验证码发送间隔（秒）
     */
    private static final int OTP_INTERVAL_SECONDS = 60;

    @Override
    @Transactional
    public UserVO register(RegisterDTO dto) {
        // 校验邮箱验证码
        String otpKey = OTP_KEY_PREFIX + dto.getEmail() + ":REGISTER";
        String cachedOtp = redisTemplate.opsForValue().get(otpKey);
        if (cachedOtp == null) {
            throw new BusinessException("验证码已过期，请重新获取");
        }
        if (!cachedOtp.equals(dto.getCode())) {
            throw new BusinessException("验证码错误");
        }
        // 验证通过，删除已使用的验证码
        redisTemplate.delete(otpKey);

        // 检查邮箱是否已注册
        LambdaQueryWrapper<SysUser> emailWrapper = new LambdaQueryWrapper<>();
        emailWrapper.eq(SysUser::getEmail, dto.getEmail());
        if (userMapper.selectCount(emailWrapper) > 0) {
            throw new BusinessException("该邮箱已注册");
        }

        // 检查用户名是否已存在
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getUsername, dto.getUsername());
        if (userMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("用户名已存在");
        }

        // 创建用户
        SysUser user = new SysUser();
        user.setId(cn.hutool.core.util.IdUtil.fastSimpleUUID());
        user.setUsername(dto.getUsername());
        user.setPassword(BCrypt.hashpw(dto.getPassword()));
        user.setName(dto.getName());
        user.setPhone(dto.getPhone());
        user.setEmail(dto.getEmail());
        user.setRole("VISITOR");
        user.setStatus("ACTIVE");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setDeleted((byte) 0);

        userMapper.insert(user);

        // 返回用户信息
        UserVO vo = new UserVO();
        BeanUtil.copyProperties(user, vo);
        return vo;
    }

    @Override
    public LoginVO login(LoginDTO dto) {
        // 查询用户
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getUsername, dto.getUsername());
        SysUser user = userMapper.selectOne(wrapper);

        if (user == null) {
            throw new BusinessException("用户名或密码错误");
        }

        // 验证密码
        if (!BCrypt.checkpw(dto.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }

        // 检查用户状态
        if ("DISABLED".equals(user.getStatus())) {
            throw new BusinessException("账号已被禁用");
        }

        // 更新最后登录时间
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);

        // 生成Token
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId(), user.getUsername());

        // 构建返回结果
        UserVO userVO = new UserVO();
        BeanUtil.copyProperties(user, userVO);

        LoginVO loginVO = new LoginVO();
        loginVO.setToken(token);
        loginVO.setRefreshToken(refreshToken);
        loginVO.setExpiresIn(86400L);
        loginVO.setUserInfo(userVO);

        return loginVO;
    }

    @Override
    public void sendOtp(SendOtpDTO dto) {
        String email = dto.getEmail();
        String type = dto.getType();

        // 检查发送间隔
        String intervalKey = OTP_INTERVAL_KEY_PREFIX + email + ":" + type;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(intervalKey))) {
            throw new BusinessException("验证码发送过于频繁，请1分钟后重试");
        }

        // 生成6位验证码
        String code = RandomUtil.randomNumbers(6);

        // 存储到Redis
        String otpKey = OTP_KEY_PREFIX + email + ":" + type;
        redisTemplate.opsForValue().set(otpKey, code, OTP_EXPIRE_MINUTES, TimeUnit.MINUTES);

        // 设置发送间隔
        redisTemplate.opsForValue().set(intervalKey, "1", OTP_INTERVAL_SECONDS, TimeUnit.SECONDS);

        // 发送邮件
        emailService.sendOtpEmail(email, code, type);

        log.info("验证码已发送至: {}, 类型: {}", email, type);
    }

    @Override
    public LoginVO loginByOtp(LoginByOtpDTO dto) {
        String email = dto.getEmail();
        String otp = dto.getOtp();

        // 验证验证码
        String otpKey = OTP_KEY_PREFIX + email + ":LOGIN";
        String cachedOtp = redisTemplate.opsForValue().get(otpKey);

        if (cachedOtp == null) {
            throw new BusinessException("验证码已过期，请重新获取");
        }

        if (!cachedOtp.equals(otp)) {
            throw new BusinessException("验证码错误");
        }

        // 删除已使用的验证码
        redisTemplate.delete(otpKey);

        // 查询用户
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getEmail, email);
        SysUser user = userMapper.selectOne(wrapper);

        if (user == null) {
            throw new BusinessException("该邮箱未注册");
        }

        // 检查用户状态
        if ("DISABLED".equals(user.getStatus())) {
            throw new BusinessException("账号已被禁用");
        }

        // 更新最后登录时间
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);

        // 生成Token
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId(), user.getUsername());

        // 构建返回结果
        UserVO userVO = new UserVO();
        BeanUtil.copyProperties(user, userVO);

        LoginVO loginVO = new LoginVO();
        loginVO.setToken(token);
        loginVO.setRefreshToken(refreshToken);
        loginVO.setExpiresIn(86400L);
        loginVO.setUserInfo(userVO);

        return loginVO;
    }

    @Override
    public void logout(String userId, String token) {
        // 将Token加入黑名单（可选，这里简单记录日志）
        log.info("用户登出: {}", userId);
    }

    @Override
    public LoginVO refreshToken(String refreshToken) {
        // 验证RefreshToken
        if (!jwtUtil.validateToken(refreshToken)) {
            throw new BusinessException("RefreshToken无效或已过期");
        }

        if (!jwtUtil.isRefreshToken(refreshToken)) {
            throw new BusinessException("Token类型错误");
        }

        // 获取用户信息
        String userId = jwtUtil.getUserIdFromToken(refreshToken);
        String username = jwtUtil.getUsernameFromToken(refreshToken);

        // 查询用户
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        // 检查用户状态
        if ("DISABLED".equals(user.getStatus())) {
            throw new BusinessException("账号已被禁用");
        }

        // 生成新的Token
        String newToken = jwtUtil.generateToken(userId, username);
        String newRefreshToken = jwtUtil.generateRefreshToken(userId, username);

        // 构建返回结果
        UserVO userVO = new UserVO();
        BeanUtil.copyProperties(user, userVO);

        LoginVO loginVO = new LoginVO();
        loginVO.setToken(newToken);
        loginVO.setRefreshToken(newRefreshToken);
        loginVO.setExpiresIn(86400L);
        loginVO.setUserInfo(userVO);

        return loginVO;
    }
}
