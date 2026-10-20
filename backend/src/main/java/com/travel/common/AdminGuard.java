package com.travel.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.travel.modules.auth.entity.User;
import com.travel.modules.auth.mapper.UserMapper;

import lombok.RequiredArgsConstructor;

/**
 * 运营接口的最小权限实现：L3 用户或 app.admin.user-ids 白名单。
 * 正式的角色体系落地后只需替换这一个入口。
 */
@Component
@RequiredArgsConstructor
public class AdminGuard {

    private final UserMapper userMapper;

    @Value("${app.admin.user-ids:}")
    private String adminUserIds;

    public void requireAdmin() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "请先登录");
        }
        if (inWhitelist(userId)) {
            return;
        }
        User user = userMapper.selectById(userId);
        if (user != null && "L3".equalsIgnoreCase(user.getUserLevel())) {
            return;
        }
        throw new BizException(ResultCode.FORBIDDEN, "需要管理员权限");
    }

    private boolean inWhitelist(Long userId) {
        if (!StringUtils.hasText(adminUserIds)) {
            return false;
        }
        for (String id : adminUserIds.split(",")) {
            if (id.trim().equals(String.valueOf(userId))) {
                return true;
            }
        }
        return false;
    }
}
