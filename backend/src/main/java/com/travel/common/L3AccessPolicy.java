package com.travel.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.travel.modules.auth.entity.User;
import com.travel.modules.auth.mapper.UserMapper;

import lombok.RequiredArgsConstructor;

/**
 * L3 或 app.admin.user-ids 白名单的唯一判定；只回答是/否，不抛业务异常。
 * 管理员与达人入口共用，避免白名单格式改动时漏改。
 */
@Component
@RequiredArgsConstructor
public class L3AccessPolicy {

    private final UserMapper userMapper;

    @Value("${app.admin.user-ids:}")
    private String trustedUserIds;

    public boolean hasAccess(Long userId) {
        if (userId == null) {
            return false;
        }
        if (inWhitelist(userId)) {
            return true;
        }
        User user = userMapper.selectById(userId);
        return user != null && "L3".equalsIgnoreCase(user.getUserLevel());
    }

    private boolean inWhitelist(Long userId) {
        if (!StringUtils.hasText(trustedUserIds)) {
            return false;
        }
        for (String id : trustedUserIds.split(",")) {
            if (id.trim().equals(String.valueOf(userId))) {
                return true;
            }
        }
        return false;
    }
}
