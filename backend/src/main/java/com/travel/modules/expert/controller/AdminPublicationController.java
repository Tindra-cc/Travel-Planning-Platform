package com.travel.modules.expert.controller;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.travel.common.AdminGuard;
import com.travel.common.Result;
import com.travel.common.UserContext;
import com.travel.modules.expert.dto.PublicationReviewRequest;
import com.travel.modules.expert.dto.PublicationReviewResponse;
import com.travel.modules.expert.service.AdminPublicationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "运营-达人发布", description = "FR-13：审核发布记录，通过后进入市场与知识库")
@RestController
@RequestMapping("/api/admin/publications")
@RequiredArgsConstructor
public class AdminPublicationController {

    private final AdminGuard adminGuard;
    private final AdminPublicationService adminPublicationService;

    @Operation(summary = "审核发布")
    @PostMapping("/{publicationId}/review")
    public Result<PublicationReviewResponse> review(@PathVariable Long publicationId,
            @Valid @RequestBody PublicationReviewRequest request) {
        adminGuard.requireAdmin();
        boolean approved = request != null && Boolean.TRUE.equals(request.getApproved());
        String reason = request == null ? null : request.getReason();
        return Result.success(adminPublicationService.review(publicationId, approved, reason,
                UserContext.getUserId()));
    }
}
