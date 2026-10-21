package com.travel.common;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * 用户等级策略的唯一入口。
 * FR-4 按已确认决策不拦截；FR-13 达人门禁复用 L3 判定。
 * 将来接入等级体系时只改这里，业务代码无需改动。
 */
@Component
@RequiredArgsConstructor
public class UserLevelPolicy {

    private final L3AccessPolicy l3AccessPolicy;

    public boolean allowMultiPlan(Long userId) {
        return true;
    }

    /** 达人工作台门禁：L3 或运营白名单。 */
    public void requireExpert(Long userId) {
        if (!l3AccessPolicy.hasAccess(userId)) {
            throw new BizException(ResultCode.FORBIDDEN, "需要达人权限（L3）");
        }
    }
}
