package com.agriculture;

import com.agriculture.common.service.EmailService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 邮件发送测试
 *
 * 使用前请先配置 application.yml 中的邮件信息：
 * spring:
 *   mail:
 *     host: smtp.qq.com
 *     port: 465
 *     username: 你的QQ邮箱@qq.com
 *     password: 你的16位授权码（不是QQ密码）
 *     properties:
 *       mail:
 *         smtp:
 *           ssl:
 *             enable: true
 *
 * 获取授权码步骤：
 * 1. 登录QQ邮箱 → 设置 → 账户
 * 2. 找到 POP3/SMTP/IMAP 服务
 * 3. 开启 POP3/SMTP 服务
 * 4. 生成授权码并复制
 */
@SpringBootTest
public class EmailTest {

    @Autowired
    private EmailService emailService;

    /**
     * 测试发送登录验证码邮件
     * 修改 to 变量为你的邮箱地址
     */
    @Test
    public void testSendLoginOtp() {
        // 修改为你的邮箱地址
        String to = "2043412933@qq.com";
        String code = "123456";

        try {
            emailService.sendOtpEmail(to, code, "LOGIN");
            System.out.println("========================================");
            System.out.println("  邮件发送成功！");
            System.out.println("  收件人: " + to);
            System.out.println("  验证码: " + code);
            System.out.println("========================================");
        } catch (Exception e) {
            System.err.println("========================================");
            System.err.println("  邮件发送失败！");
            System.err.println("  错误信息: " + e.getMessage());
            System.err.println("========================================");
            e.printStackTrace();
        }
    }

    /**
     * 测试发送注册验证码邮件
     */
    @Test
    public void testSendRegisterOtp() {
        String to = "your-email@qq.com";
        String code = "654321";

        try {
            emailService.sendOtpEmail(to, code, "REGISTER");
            System.out.println("========================================");
            System.out.println("  注册验证码邮件发送成功！");
            System.out.println("  收件人: " + to);
            System.out.println("  验证码: " + code);
            System.out.println("========================================");
        } catch (Exception e) {
            System.err.println("  邮件发送失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
