package com.travel.modules.guidebook.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.travel.common.BizException;
import com.travel.common.ResultCode;
import com.travel.modules.guidebook.dto.FreshnessDiffItem;
import com.travel.modules.guidebook.dto.GenerateGuidebookResponse;
import com.travel.modules.guidebook.dto.ReferenceGuidebook;
import com.travel.modules.guidebook.dto.RegenerateResponse;
import com.travel.modules.guidebook.entity.GuidebookShare;
import com.travel.modules.guidebook.mapper.GuidebookShareMapper;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;

/** FR-12 阶段 B：公开路径内的「需登录」入口，显式校验登录并使用 Sa-Token 的登录 ID。 */
@Service
@RequiredArgsConstructor
public class ShareRegenerationService {

    private final GuidebookShareMapper shareMapper;
    private final ShareReferenceBuilder referenceBuilder;
    private final ShareFreshnessService freshnessService;
    private final GuidebookService guidebookService;

    public RegenerateResponse regenerate(String shareCode, Long sessionId) {
        StpUtil.checkLogin();
        Long userId = StpUtil.getLoginIdAsLong();

        GuidebookShare share = shareMapper.selectOne(Wrappers.<GuidebookShare>lambdaQuery()
                .eq(GuidebookShare::getShareCode, shareCode));
        if (share == null) {
            throw new BizException(ResultCode.NOT_FOUND, "分享不存在");
        }
        if (!GuidebookShare.STATUS_ACTIVE.equals(share.getStatus())) {
            throw new BizException(ResultCode.CONFLICT, "分享已撤回");
        }
        if (share.getExpiresAt() != null && !share.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new BizException(ResultCode.CONFLICT, "分享已过期");
        }

        ReferenceGuidebook reference = referenceBuilder.build(share.getSnapshot());
        reference.setTitle(share.getTitle());

        GenerateGuidebookResponse created = guidebookService.generateWithReference(
                userId, sessionId, reference, share.getId());

        RegenerateResponse resp = new RegenerateResponse();
        resp.setGuidebookId(created.getGuidebookId());
        resp.setStatus(created.getStatus());
        resp.setDataFreshnessDiff(freshnessService.diff(share.getSnapshot(), LocalDateTime.now()));
        return resp;
    }
}
