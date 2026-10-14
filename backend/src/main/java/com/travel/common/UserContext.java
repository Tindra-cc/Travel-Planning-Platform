package com.travel.common;

import cn.dev33.satoken.stp.StpUtil;
import lombok.Getter;
import lombok.Setter;

/**
 * 当前登录用户上下文
 * UserContext
 */
public class UserContext {

    private  static final ThreadLocal<LoginUser> USER_HOLDER = new ThreadLocal<>();

    @Getter 
    @Setter 
    public static class LoginUser{
        private Long userId;
        private String username;
    }

    /**
     * 设置当前用户
     */

    public static void set(LoginUser user){
        USER_HOLDER.set(user);
    }

    /**
     * 获取当前用户
     */
    public static LoginUser get(){
        return USER_HOLDER.get();
    }

    /**
     * 清除上下文
     */
    public static void clear(){
        USER_HOLDER.remove();
    }

    /**
     * 获取当前登录用户ID Sa-Token兜底
     */
    public static Long getUserId(){
        LoginUser user = get();
        if (user != null && user.getUserId() != null) {
            return user.getUserId();
        }

        //Sa-Token 从token中获取ID
        return StpUtil.getLoginIdAsLong();
    }
}
