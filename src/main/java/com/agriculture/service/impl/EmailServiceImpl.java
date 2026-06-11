package com.agriculture.service.impl;

import com.agriculture.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * 邮件服务实现类（QQ邮箱SMTP）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Override
    public void sendOtpEmail(String to, String code, String type) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(to);

            String subject;
            String text;

            switch (type) {
                case "LOGIN":
                    subject = "【智慧农业系统】登录验证码";
                    text = "尊敬的用户：\n\n"
                            + "您的登录验证码是：" + code + "\n"
                            + "有效期5分钟，请勿泄露给他人。\n\n"
                            + "如非本人操作，请忽略此邮件。\n\n"
                            + "智慧农业病虫害监测系统";
                    break;
                case "REGISTER":
                    subject = "【智慧农业系统】注册验证码";
                    text = "尊敬的用户：\n\n"
                            + "您的注册验证码是：" + code + "\n"
                            + "有效期5分钟，请勿泄露给他人。\n\n"
                            + "智慧农业病虫害监测系统";
                    break;
                case "RESET_PASSWORD":
                    subject = "【智慧农业系统】重置密码验证码";
                    text = "尊敬的用户：\n\n"
                            + "您的重置密码验证码是：" + code + "\n"
                            + "有效期5分钟，请勿泄露给他人。\n\n"
                            + "如非本人操作，请忽略此邮件。\n\n"
                            + "智慧农业病虫害监测系统";
                    break;
                default:
                    subject = "【智慧农业系统】验证码";
                    text = "尊敬的用户：\n\n"
                            + "您的验证码是：" + code + "\n"
                            + "有效期5分钟，请勿泄露给他人。\n\n"
                            + "智慧农业病虫害监测系统";
            }

            message.setSubject(subject);
            message.setText(text);

            mailSender.send(message);
            log.info("验证码邮件已发送至: {}", to);
        } catch (Exception e) {
            log.error("发送邮件失败: {}", e.getMessage(), e);
            throw new RuntimeException("邮件发送失败，请稍后重试");
        }
    }
}
