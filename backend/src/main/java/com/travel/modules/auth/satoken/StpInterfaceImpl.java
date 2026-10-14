package com.travel.modules.auth.satoken;

import java.util.List;

import org.springframework.stereotype.Component;

import com.travel.modules.auth.entity.User;
import com.travel.modules.auth.mapper.UserMapper;

import cn.dev33.satoken.stp.StpInterface;
import lombok.RequiredArgsConstructor;

/**
 * Sa-Token 角色/权限数据源：角色即用户层 L1/L2/L3。
 */
@Component
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {

    private final UserMapper userMapper;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return List.of();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        User user = userMapper.selectById(Long.valueOf(loginId.toString()));
        if (user == null || user.getUserLevel() == null) {
            return List.of();
        }
        return List.of(user.getUserLevel());
    }
}
