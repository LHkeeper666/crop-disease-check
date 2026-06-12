package com.agriculture.common.aspect;

import com.agriculture.common.annotation.RequireRole;
import com.agriculture.modules.user.mapper.SysUserMapper;
import com.agriculture.modules.user.entity.SysUser;
import com.agriculture.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;

/**
 * 权限校验切面
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class PermissionAspect {

    private final SysUserMapper userMapper;

    /**
     * 校验角色权限
     */
    @Before("@annotation(requireRole)")
    public void checkPermission(JoinPoint joinPoint, RequireRole requireRole) {
        // 获取当前请求
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            throw new BusinessException("获取请求信息失败");
        }

        HttpServletRequest request = attributes.getRequest();

        // 获取当前用户ID
        String userId = (String) request.getAttribute("userId");
        if (userId == null) {
            throw new BusinessException(401, "用户未登录");
        }

        // 查询用户信息
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(401, "用户不存在");
        }

        // 检查用户状态
        if ("DISABLED".equals(user.getStatus())) {
            throw new BusinessException(403, "账号已被禁用");
        }

        // 获取允许的角色
        String[] allowedRoles = requireRole.value();

        // 校验角色
        boolean hasPermission = Arrays.asList(allowedRoles).contains(user.getRole());
        if (!hasPermission) {
            log.warn("用户 {} (角色: {}) 尝试访问需要 {} 的接口",
                    user.getUsername(), user.getRole(), Arrays.toString(allowedRoles));
            throw new BusinessException(403, "权限不足，需要角色: " + Arrays.toString(allowedRoles));
        }

        log.debug("用户 {} (角色: {}) 权限校验通过", user.getUsername(), user.getRole());
    }
}
