package com.travel.config;

import org.springframework.web.servlet.HandlerInterceptor;

import com.travel.common.UserContext;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 登录后把当前用户写入 ThreadLocal 上下文，请求结束务必清理，防止线程复用串号。
 */
public class UserContextInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (StpUtil.isLogin()) {
            UserContext.set(new UserContext.LoginUser(StpUtil.getLoginIdAsLong(), null));
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}
