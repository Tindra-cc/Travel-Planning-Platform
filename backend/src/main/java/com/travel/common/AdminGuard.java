package com.travel.common;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * 运营接口的最小权限实现：L3 用户或 app.admin.user-ids 白名单。
 * 判定委托 {@link L3AccessPolicy}，正式的角色体系落地后只需替换这一处。
 */
@Component
@RequiredArgsConstructor
public class AdminGuard {

    private final L3AccessPolicy l3AccessPolicy;

    public void requireAdmin() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "请先登录");
        }
        if (!l3AccessPolicy.hasAccess(userId)) {
            throw new BizException(ResultCode.FORBIDDEN, "需要管理员权限");
        }
    }
}
