package com.agriculture.common.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Properties;

/**
 * 邮件服务实现类（QQ邮箱SMTP）
 * 启动时预建SMTP连接，发送时直接复用，省去每次的SSL握手+认证耗时
 */
@Slf4j
@Service
public class EmailServiceImpl implements EmailService {

    @Value("${spring.mail.host}")
    private String host;

    @Value("${spring.mail.port}")
    private int port;

    @Value("${spring.mail.username}")
    private String username;

    @Value("${spring.mail.password}")
    private String password;

    @Value("${spring.mail.properties.mail.smtp.connectiontimeout:5000}")
    private int connectionTimeout;

    @Value("${spring.mail.properties.mail.smtp.timeout:5000}")
    private int timeout;

    @Value("${spring.mail.properties.mail.smtp.writetimeout:5000}")
    private int writeTimeout;

    private Session session;
    private Transport transport;
    private final Object lock = new Object();

    /**
     * 启动时预建SMTP连接
     */
    @PostConstruct
    public void init() {
        Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", String.valueOf(port));
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.ssl.enable", "true");
        props.put("mail.smtp.connectiontimeout", String.valueOf(connectionTimeout));
        props.put("mail.smtp.timeout", String.valueOf(timeout));
        props.put("mail.smtp.writetimeout", String.valueOf(writeTimeout));

        session = Session.getInstance(props);
        warmUp();
    }

    /**
     * 预建连接（启动时调用，断线时自动重连）
     */
    private void warmUp() {
        synchronized (lock) {
            try {
                if (transport != null && transport.isConnected()) {
                    return;
                }
                long start = System.currentTimeMillis();
                transport = session.getTransport("smtp");
                transport.connect(host, port, username, password);
                log.info("SMTP连接已预建，耗时: {}ms", System.currentTimeMillis() - start);
            } catch (MessagingException e) {
                log.warn("SMTP预建连接失败（首次发送时会重试）: {}", e.getMessage());
                transport = null;
            }
        }
    }

    /**
     * 确保连接可用，断线自动重连
     */
    private Transport getConnectedTransport() throws MessagingException {
        synchronized (lock) {
            if (transport != null && transport.isConnected()) {
                return transport;
            }
            // 连接已断，重建
            log.info("SMTP连接已断开，正在重建...");
            long start = System.currentTimeMillis();
            transport = session.getTransport("smtp");
            transport.connect(host, port, username, password);
            log.info("SMTP重连完成，耗时: {}ms", System.currentTimeMillis() - start);
            return transport;
        }
    }

    @Override
    public void sendOtpEmail(String to, String code, String type) {
        try {
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

            MimeMessage message = new MimeMessage(session);
            message.setFrom(username);
            message.setRecipients(MimeMessage.RecipientType.TO, to);
            message.setSubject(subject);
            message.setText(text);

            long start = System.currentTimeMillis();
            Transport t = getConnectedTransport();
            t.sendMessage(message, message.getAllRecipients());
            log.info("邮件已发送至: {}, 类型: {}, 耗时: {}ms", to, type, System.currentTimeMillis() - start);

        } catch (MessagingException e) {
            log.error("发送邮件失败: {}", e.getMessage(), e);
            // 发送失败时丢弃旧连接，下次自动重建
            synchronized (lock) {
                if (transport != null) {
                    try { transport.close(); } catch (MessagingException ignored) {}
                    transport = null;
                }
            }
            throw new RuntimeException("邮件发送失败，请稍后重试");
        }
    }

    @Override
    public void sendEmail(String to, String subject, String text) {
        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(username);
            message.setRecipients(MimeMessage.RecipientType.TO, to);
            message.setSubject(subject);
            message.setText(text);

            long start = System.currentTimeMillis();
            Transport t = getConnectedTransport();
            t.sendMessage(message, message.getAllRecipients());
            log.info("邮件已发送至: {}, 主题: {}, 耗时: {}ms", to, subject, System.currentTimeMillis() - start);

        } catch (MessagingException e) {
            log.error("发送邮件失败: {}", e.getMessage(), e);
            synchronized (lock) {
                if (transport != null) {
                    try { transport.close(); } catch (MessagingException ignored) {}
                    transport = null;
                }
            }
            throw new RuntimeException("邮件发送失败，请稍后重试");
        }
    }

    @PreDestroy
    public void destroy() {
        synchronized (lock) {
            if (transport != null && transport.isConnected()) {
                try {
                    transport.close();
                    log.info("SMTP连接已关闭");
                } catch (MessagingException e) {
                    log.warn("关闭SMTP连接异常: {}", e.getMessage());
                }
            }
        }
    }
}
