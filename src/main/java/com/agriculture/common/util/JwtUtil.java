package com.agriculture.common.util;

import cn.hutool.core.date.DateUtil;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import cn.hutool.jwt.RegisteredPayload;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * JWT工具类
 */
@Component
public class JwtUtil {

    @Value("${jwt.secret:agriculture-secret-key-2026}")
    private String secret;

    @Value("${jwt.expiration:86400}")
    private Long expiration;

    @Value("${jwt.refresh-expiration:604800}")
    private Long refreshExpiration;

    /**
     * 生成AccessToken
     */
    public String generateToken(String userId, String username) {
        Date now = new Date();
        Date expireDate = DateUtil.offsetSecond(now, expiration.intValue());

        JWT jwt = JWT.create()
                .setPayload("userId", userId)
                .setPayload("username", username)
                .setPayload("type", "access")
                .setPayload(RegisteredPayload.ISSUED_AT, now)
                .setPayload(RegisteredPayload.EXPIRES_AT, expireDate)
                .setKey(secret.getBytes());

        return jwt.sign();
    }

    /**
     * 生成RefreshToken
     */
    public String generateRefreshToken(String userId, String username) {
        Date now = new Date();
        Date expireDate = DateUtil.offsetSecond(now, refreshExpiration.intValue());

        JWT jwt = JWT.create()
                .setPayload("userId", userId)
                .setPayload("username", username)
                .setPayload("type", "refresh")
                .setPayload(RegisteredPayload.ISSUED_AT, now)
                .setPayload(RegisteredPayload.EXPIRES_AT, expireDate)
                .setKey(secret.getBytes());

        return jwt.sign();
    }

    /**
     * 从Token中获取用户ID
     */
    public String getUserIdFromToken(String token) {
        JWT jwt = JWTUtil.parseToken(token).setKey(secret.getBytes());
        return jwt.getPayload("userId").toString();
    }

    /**
     * 从Token中获取用户名
     */
    public String getUsernameFromToken(String token) {
        JWT jwt = JWTUtil.parseToken(token).setKey(secret.getBytes());
        return jwt.getPayload("username").toString();
    }

    /**
     * 从Token中获取类型
     */
    public String getTypeFromToken(String token) {
        JWT jwt = JWTUtil.parseToken(token).setKey(secret.getBytes());
        Object type = jwt.getPayload("type");
        return type != null ? type.toString() : "access";
    }

    /**
     * 验证Token是否有效
     */
    public boolean validateToken(String token) {
        try {
            JWT jwt = JWTUtil.parseToken(token).setKey(secret.getBytes());
            return jwt.validate(0);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 验证是否为RefreshToken
     */
    public boolean isRefreshToken(String token) {
        return "refresh".equals(getTypeFromToken(token));
    }
}
