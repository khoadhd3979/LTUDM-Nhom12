package com.securechat.utils;

import com.securechat.config.AppConfig;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.util.Properties;

public final class MailUtils {
    
    private MailUtils() {}

    public static void sendOtp(String toEmail, String otp) {
        if (!AppConfig.mailEnabled()) {
            // Development mode — only print when MAIL_ENABLED=false.
            System.out.println("[MAIL-DEV] OTP cho " + toEmail + " = " + otp);
            return;
        }
        
        String user = AppConfig.mailUser();
        String pass = AppConfig.mailPassword();
        
        if (user == null || pass == null) {
            throw new IllegalStateException(
                    "MAIL_ENABLED=true nhưng thiếu SC_MAIL_USER/SC_MAIL_PASSWORD");
        }
        
        try {
            Properties props = new Properties();
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.host", AppConfig.mailHost());
            props.put("mail.smtp.port", String.valueOf(AppConfig.mailPort()));

            Session session = Session.getInstance(props, new Authenticator() {
                @Override 
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(user, pass);
                }
            });

            Message msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(AppConfig.mailFrom()));
            msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
            msg.setSubject("SecureChat - Mã xác thực OTP");
            msg.setText("Mã OTP của bạn là: " + otp + "\nHiệu lực 10 phút.");
            
            Transport.send(msg);
        } catch (MessagingException e) {
            // KHÔNG đưa OTP vào message lỗi để tránh lộ lọt bảo mật.
            throw new RuntimeException("Gửi email thất bại: " + e.getMessage(), e);
        }
    }
}