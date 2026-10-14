package com.travel.modules.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.travel.common.BizException;
import com.travel.common.Masks;
import com.travel.common.ResultCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 验证码邮件发送（QQ 邮箱 SMTP）。凭据走环境变量，未配置时由调用方降级处理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String from;

    @Value("${spring.mail.password:}")
    private String authCode;

    public boolean configured() {
        return StringUtils.hasText(from) && StringUtils.hasText(authCode);
    }

    public void sendVerifyCode(String to, String code, String scene) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject("【AI 旅行规划助理】" + sceneLabel(scene) + "验证码");
        message.setText("""
                您好，

                您正在%s，验证码为：%s

                验证码 5 分钟内有效，请勿泄露给他人。
                如非本人操作，请忽略本邮件。

                —— AI 旅行规划助理
                """.formatted(sceneLabel(scene), code));
        try {
            mailSender.send(message);
            log.info("验证码邮件已发送至 {}", Masks.account(to));
        } catch (Exception e) {
            log.error("验证码邮件发送失败 to={}: {}", Masks.account(to), e.getMessage());
            throw new BizException(ResultCode.DEPENDENCY_UNAVAILABLE, "验证码邮件发送失败，请稍后重试");
        }
    }

    private String sceneLabel(String scene) {
        return switch (scene) {
            case "register" -> "注册";
            case "login" -> "登录";
            case "reset-password" -> "重置密码";
            default -> "身份验证";
        };
    }
}
