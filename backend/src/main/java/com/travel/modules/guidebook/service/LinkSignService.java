package com.travel.modules.guidebook.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * 转链签名：HMAC-SHA256(资源 ID + ":" + secret)。
 * 未配置密钥时使用进程内随机密钥并告警，避免线上误用可预测的默认值。
 */
@Slf4j
@Component
public class LinkSignService {

    private static final String ALGORITHM = "HmacSHA256";

    private final byte[] secret;

    public LinkSignService(@Value("${app.link.signing-secret:}") String configured) {
        if (StringUtils.hasText(configured)) {
            this.secret = configured.getBytes(StandardCharsets.UTF_8);
        } else {
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            this.secret = random;
            log.warn("未配置 app.link.signing-secret，已使用进程内随机密钥；重启后旧转链将失效");
        }
    }

    public String sign(String resourceSlotId) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            byte[] digest = mac.doFinal(
                    (resourceSlotId + ":").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("转链签名失败", e);
        }
    }

    public boolean verify(String resourceSlotId, String signature) {
        if (!StringUtils.hasText(resourceSlotId) || !StringUtils.hasText(signature)) {
            return false;
        }
        byte[] expected = sign(resourceSlotId).getBytes(StandardCharsets.UTF_8);
        byte[] actual = signature.toLowerCase().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }
}
