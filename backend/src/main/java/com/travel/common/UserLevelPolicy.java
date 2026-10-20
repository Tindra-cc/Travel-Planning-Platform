package com.travel.common;

import org.springframework.stereotype.Component;

/**
 * 用户等级策略的唯一入口。
 * PRD 要求 FR-4 仅 L2 可用，但当前无等级体系；本期按已确认决策不拦截，
 * 将来接入等级时只改这里，业务代码无需改动。
 */
@Component
public class UserLevelPolicy {

    public boolean allowMultiPlan(Long userId) {
        return true;
    }
}
