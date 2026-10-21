package com.travel.modules.guidebook.service;

import java.security.SecureRandom;

/**
 * 分享短码是公开访问的唯一凭证，必须不可枚举：
 * 16 位 Base62 约 95 bit，使用 SecureRandom。
 */
public final class ShareCodeGenerator {

    public static final int CODE_LENGTH = 16;

    private static final char[] BASE62 =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private ShareCodeGenerator() {
    }

    public static String generate() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(BASE62[RANDOM.nextInt(BASE62.length)]);
        }
        return sb.toString();
    }
}
