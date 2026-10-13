package com.securechat.utils;

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
    private static final boolean MAIL_ENABLED =
            Boolean.parseBoolean(System.getenv().getOrDefault("SC_MAIL_ENABLED", "false"));
    private static final String SMTP_HOST = System.getenv().getOrDefault("SC_MAIL_HOST", "smtp.gmail.com");
    private static final int    SMTP_PORT = Integer.parseInt(System.getenv().getOrDefault("SC_MAIL_PORT", "587"));
    private static final String SMTP_USER = System.getenv("SC_MAIL_USER");
    private static final String SMTP_PASS = System.getenv("SC_MAIL_PASSWORD");
    private static final String FROM      = System.getenv().getOrDefault("SC_MAIL_FROM",
                                                SMTP_USER == null ? "" : SMTP_USER);

    private MailUtils() {}

    public static void sendOtp(String toEmail, String otp) {
        if (!MAIL_ENABLED || SMTP_USER == null || SMTP_PASS == null) {
            // Chỉ dùng cho dev — không rò secret, chỉ in OTP khi cờ tắt.
            System.out.println("[MAIL-DEV] OTP cho " + toEmail + " = " + otp);
            return;
        }
        try {
            Properties props = new Properties();
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.host", SMTP_HOST);
            props.put("mail.smtp.port", String.valueOf(SMTP_PORT));

            Session session = Session.getInstance(props, new Authenticator() {
                @Override protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(SMTP_USER, SMTP_PASS);
                }
            });

            Message msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(FROM));
            msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
            msg.setSubject("SecureChat - Mã xác thực OTP");
            msg.setText("Mã OTP của bạn là: " + otp + "\nHiệu lực 10 phút.");
            Transport.send(msg);
        } catch (MessagingException e) {
            // KHÔNG đưa OTP vào message lỗi.
            throw new RuntimeException("Gửi email thất bại: " + e.getMessage(), e);
        }
    }
}