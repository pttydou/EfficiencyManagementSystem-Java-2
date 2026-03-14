package com.efficiency.service;

import javax.mail.*;
import javax.mail.internet.*;
import java.util.Properties;
import java.util.function.Consumer;

/**
 * 邮件发送器
 * 对应原C++ EmailSender类
 * 使用JavaMail通过SMTP发送验证码邮件
 */
public class EmailSender {
    private String smtpServer = "smtp.qq.com";
    private int smtpPort = 465;
    private String senderEmail = "1572015465@qq.com";
    private String senderAuthCode = "code"; // 授权码

    // 回调
    private Runnable onSendSuccess;
    private Consumer<String> onSendFailed;

    public EmailSender() {}

    public void setSmtpConfig(String server, int port, String senderEmail, String authCode) {
        this.smtpServer = server;
        this.smtpPort = port;
        this.senderEmail = senderEmail;
        this.senderAuthCode = authCode;
    }

    public void setOnSendSuccess(Runnable callback) {
        this.onSendSuccess = callback;
    }

    public void setOnSendFailed(Consumer<String> callback) {
        this.onSendFailed = callback;
    }

    /**
     * 异步发送验证码邮件
     */
    public void sendVerificationCode(String recipientEmail, String code) {
        new Thread(() -> {
            try {
                Properties props = new Properties();
                props.put("mail.smtp.host", smtpServer);
                props.put("mail.smtp.port", String.valueOf(smtpPort));
                props.put("mail.smtp.auth", "true");
                props.put("mail.smtp.ssl.enable", "true");
                props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");

                Session session = Session.getInstance(props, new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(senderEmail, senderAuthCode);
                    }
                });

                MimeMessage message = new MimeMessage(session);
                message.setFrom(new InternetAddress(senderEmail));
                message.setRecipient(Message.RecipientType.TO, new InternetAddress(recipientEmail));
                message.setSubject("你的验证码");
                message.setText("你的验证码是：" + code + "，5分钟内有效。\n请勿向他人泄露验证码。");

                Transport.send(message);
                System.out.println("✅ 验证码邮件发送成功，收件人: " + recipientEmail);
                if (onSendSuccess != null) onSendSuccess.run();

            } catch (MessagingException e) {
                String errorMsg = "邮件发送失败: " + e.getMessage();
                System.err.println("❌ " + errorMsg);
                if (onSendFailed != null) onSendFailed.accept(errorMsg);
            }
        }).start();
    }
}
