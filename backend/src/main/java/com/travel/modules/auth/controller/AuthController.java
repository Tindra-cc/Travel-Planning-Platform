package com.travel.modules.auth.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.travel.common.Result;
import com.travel.modules.auth.dto.AuthResponse;
import com.travel.modules.auth.dto.CurrentUserResponse;
import com.travel.modules.auth.dto.LoginRequest;
import com.travel.modules.auth.dto.RegisterRequest;
import com.travel.modules.auth.dto.UpdateProfileRequest;
import com.travel.modules.auth.dto.VerifyCodeRequest;
import com.travel.modules.auth.dto.VerifyCodeResponse;
import com.travel.modules.auth.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "鉴权", description = "注册 / 登录 / 验证码 / 当前用户")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "发送验证码")
    @SecurityRequirements
    @PostMapping("/verify-code")
    public Result<VerifyCodeResponse> verifyCode(@Valid @RequestBody VerifyCodeRequest req) {
        return Result.success(authService.sendVerifyCode(req));
    }

    @Operation(summary = "注册")
    @SecurityRequirements
    @PostMapping("/register")
    public Result<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        return Result.success(authService.register(req));
    }

    @Operation(summary = "登录：返回 token，供 Authorize 使用")
    @SecurityRequirements
    @PostMapping("/login")
    public Result<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return Result.success(authService.login(req));
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout();
        return Result.success();
    }

    @GetMapping("/me")
    public Result<CurrentUserResponse> me() {
        return Result.success(authService.me());
    }

    @Operation(summary = "修改昵称与头像")
    @PutMapping("/me")
    public Result<CurrentUserResponse> updateMe(@Valid @RequestBody UpdateProfileRequest req) {
        return Result.success(authService.updateProfile(req));
    }
}
