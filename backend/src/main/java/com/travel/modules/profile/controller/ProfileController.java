package com.travel.modules.profile.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.travel.common.Result;
import com.travel.modules.profile.dto.ProfileCheckResponse;
import com.travel.modules.profile.dto.ProfileResponse;
import com.travel.modules.profile.dto.ProfileUpdateRequest;
import com.travel.modules.profile.dto.TravelerRequest;
import com.travel.modules.profile.dto.TravelerResponse;
import com.travel.modules.profile.service.ProfileService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "用户画像", description = "FR-1：画像读写 / 完整性检查 / 出行人管理")
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping
    public Result<ProfileResponse> getProfile() {
        return Result.success(profileService.getProfile());
    }

    @PutMapping
    public Result<ProfileResponse> updateProfile(@RequestBody ProfileUpdateRequest req) {
        return Result.success(profileService.updateProfile(req));
    }

    @PostMapping("/check")
    public Result<ProfileCheckResponse> check() {
        return Result.success(profileService.check());
    }

    @GetMapping("/travelers")
    public Result<List<TravelerResponse>> listTravelers() {
        return Result.success(profileService.listTravelers());
    }

    @PostMapping("/travelers")
    public Result<TravelerResponse> addTraveler(@Valid @RequestBody TravelerRequest req) {
        return Result.success(profileService.addTraveler(req));
    }

    @PutMapping("/travelers/{travelerId}")
    public Result<TravelerResponse> updateTraveler(@PathVariable Long travelerId,
                                                   @Valid @RequestBody TravelerRequest req) {
        return Result.success(profileService.updateTraveler(travelerId, req));
    }

    @DeleteMapping("/travelers/{travelerId}")
    public Result<Void> deleteTraveler(@PathVariable Long travelerId) {
        profileService.deleteTraveler(travelerId);
        return Result.success();
    }
}
