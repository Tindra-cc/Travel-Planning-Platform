package com.travel.modules.guidebook.controller;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.travel.common.Result;
import com.travel.modules.guidebook.dto.LinkValidateRequest;
import com.travel.modules.guidebook.dto.LinkValidateResponse;
import com.travel.modules.guidebook.service.ResourceService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "转链", description = "FR-8：签名跳转与有效性校验")
@RestController
@RequestMapping("/api/link")
@RequiredArgsConstructor
public class LinkController {

    private final ResourceService resourceService;

    @Operation(summary = "签名跳转（302）")
    @GetMapping("/click")
    public ResponseEntity<Void> click(@RequestParam("rid") String resourceSlotId,
            @RequestParam("sig") String signature) {
        String target = resourceService.resolveClick(resourceSlotId, signature);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(target)).build();
    }

    @Operation(summary = "批量校验转链有效性")
    @PostMapping("/validate")
    public Result<LinkValidateResponse> validate(@RequestBody LinkValidateRequest request) {
        return Result.success(resourceService.validate(
                request == null ? null : request.getResourceSlotIds()));
    }
}
