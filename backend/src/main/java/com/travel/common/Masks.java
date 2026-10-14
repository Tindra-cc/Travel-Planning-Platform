package com.travel.common;

import org.springframework.util.StringUtils;

/** 日志脱敏：不把完整账号写进日志。 */
public final class Masks {

    private Masks() {
    }

    /** 邮箱 a***@qq.com；手机号 138****0001；其他保留首尾。 */
    public static String account(String account) {
        if (!StringUtils.hasText(account)) {
            return "<empty>";
        }
        int at = account.indexOf('@');
        if (at > 0) {
            return account.charAt(0) + "***" + account.substring(at);
        }
        if (account.length() >= 7) {
            return account.substring(0, 3) + "****" + account.substring(account.length() - 4);
        }
        return account.charAt(0) + "***";
    }
}
