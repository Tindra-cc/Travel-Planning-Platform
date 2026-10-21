package com.travel.modules.expert.config;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.travel.common.UserContext;
import com.travel.common.UserLevelPolicy;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/** /api/expert/** 的统一达人门禁；登录仍由 Sa-Token 负责。 */
@Component
@RequiredArgsConstructor
public class ExpertInterceptor implements HandlerInterceptor {

    private final UserLevelPolicy userLevelPolicy;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        userLevelPolicy.requireExpert(UserContext.getUserId());
        return true;
    }
}
