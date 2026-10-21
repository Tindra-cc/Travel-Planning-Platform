package com.travel.modules.guidebook.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.travel.common.BizException;
import com.travel.common.ResultCode;
import com.travel.common.UserContext;
import com.travel.modules.guidebook.config.ShareProperties;
import com.travel.modules.guidebook.dto.ShareCreateRequest;
import com.travel.modules.guidebook.dto.ShareCreateResponse;
import com.travel.modules.guidebook.dto.SharedGuidebookResponse;
import com.travel.modules.guidebook.dto.SharedGuidebookSnapshot;
import com.travel.modules.guidebook.entity.Guidebook;
import com.travel.modules.guidebook.entity.GuidebookShare;
import com.travel.modules.guidebook.mapper.GuidebookShareMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShareService {

    private static final List<String> SHAREABLE_STATUSES =
            List.of(Guidebook.STATUS_CONFIRMED, Guidebook.STATUS_EXECUTING,
                    Guidebook.STATUS_COMPLETED);
    private static final int MAX_INSERT_ATTEMPTS = 3;
    private static final int TITLE_MAX_LENGTH = 200;

    private final GuidebookService guidebookService;
    private final GuidebookShareMapper shareMapper;
    private final ShareSnapshotBuilder snapshotBuilder;
    private final ShareProperties shareProperties;

    /** §12.1 创建分享；同一攻略 + 渠道 + 版本幂等，新版本会先撤回旧分享。 */
    @Transactional
    public ShareCreateResponse create(Long guidebookId, ShareCreateRequest req) {
        Guidebook guidebook = guidebookService.requireOwned(guidebookId);
        if (!SHAREABLE_STATUSES.contains(guidebook.getStatus())) {
            throw new BizException(ResultCode.CONFLICT, "当前状态不支持分享");
        }
        String channel = normalizeChannel(req.getChannel());

        GuidebookShare existing = findActive(guidebook.getId(), channel);
        if (existing != null && Objects.equals(existing.getGuidebookVersion(), guidebook.getVersion())) {
            return toCreateResponse(existing);
        }
        if (existing != null) {
            revokeActive(existing.getId());
        }

        GuidebookShare share = new GuidebookShare();
        share.setGuidebookId(guidebook.getId());
        share.setOwnerId(UserContext.getUserId());
        share.setGuidebookVersion(guidebook.getVersion());
        share.setChannel(channel);
        share.setTitle(truncate(guidebook.getTitle(), TITLE_MAX_LENGTH));
        share.setSnapshot(snapshotBuilder.build(guidebook));
        share.setStatus(GuidebookShare.STATUS_ACTIVE);
        share.setExpiresAt(expiry());

        GuidebookShare saved = insertWithUniqueCode(share, guidebook, channel);
        return toCreateResponse(saved);
    }

    /** §12.2 公开读取；不接收也不读取用户上下文。 */
    public SharedGuidebookResponse getPublic(String shareCode) {
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
        return toPublicResponse(share);
    }

    /** §12.4 撤回是状态变更，快照保留；重复撤回幂等。 */
    @Transactional
    public void revoke(Long guidebookId, String shareCode) {
        GuidebookShare share = shareMapper.selectOne(Wrappers.<GuidebookShare>lambdaQuery()
                .eq(GuidebookShare::getShareCode, shareCode)
                .eq(GuidebookShare::getGuidebookId, guidebookId));
        if (share == null) {
            throw new BizException(ResultCode.NOT_FOUND, "分享不存在");
        }
        if (!Objects.equals(share.getOwnerId(), UserContext.getUserId())) {
            throw new BizException(ResultCode.NOT_OWNER, "无权撤回该分享");
        }
        if (GuidebookShare.STATUS_ACTIVE.equals(share.getStatus())) {
            revokeActive(share.getId());
        }
    }

    private GuidebookShare insertWithUniqueCode(GuidebookShare share, Guidebook guidebook,
            String channel) {
        for (int attempt = 0; attempt < MAX_INSERT_ATTEMPTS; attempt++) {
            share.setId(null);
            share.setShareCode(ShareCodeGenerator.generate());
            try {
                shareMapper.insert(share);
                return share;
            } catch (DuplicateKeyException e) {
                GuidebookShare active = findActive(guidebook.getId(), channel);
                if (active != null) {
                    if (Objects.equals(active.getGuidebookVersion(), guidebook.getVersion())) {
                        return active;
                    }
                    throw new BizException(ResultCode.CONFLICT, "分享状态已被并发更新，请重试");
                }
                log.warn("分享短码冲突，重新生成 attempt={}", attempt + 1);
            }
        }
        throw new BizException(ResultCode.FAIL, "生成分享短码失败，请重试");
    }

    private void revokeActive(Long shareId) {
        shareMapper.update(null, Wrappers.<GuidebookShare>update()
                .eq("id", shareId)
                .eq("status", GuidebookShare.STATUS_ACTIVE)
                .set("status", GuidebookShare.STATUS_REVOKED));
    }

    private GuidebookShare findActive(Long guidebookId, String channel) {
        return shareMapper.selectOne(Wrappers.<GuidebookShare>lambdaQuery()
                .eq(GuidebookShare::getGuidebookId, guidebookId)
                .eq(GuidebookShare::getChannel, channel)
                .eq(GuidebookShare::getStatus, GuidebookShare.STATUS_ACTIVE));
    }

    private String normalizeChannel(String channel) {
        if (GuidebookShare.CHANNEL_LINK.equals(channel)
                || GuidebookShare.CHANNEL_QRCODE.equals(channel)) {
            return channel;
        }
        if ("market".equals(channel)) {
            throw new BizException(ResultCode.UNPROCESSABLE, "市场渠道由达人发布流程提供");
        }
        throw new BizException(ResultCode.PARAM_ERROR, "不支持的分享渠道");
    }

    private LocalDateTime expiry() {
        return shareProperties.getExpireDays() <= 0
                ? null
                : LocalDateTime.now().plusDays(shareProperties.getExpireDays());
    }

    private ShareCreateResponse toCreateResponse(GuidebookShare share) {
        String url = shareProperties.getBaseUrl().replaceAll("/+$", "")
                + "/share/" + share.getShareCode();
        String template = shareProperties.getQrcodeUrlTemplate();
        String qrcodeUrl = StringUtils.hasText(template)
                ? template.replace("{url}", url)
                : null;
        return new ShareCreateResponse(share.getShareCode(), url, qrcodeUrl);
    }

    private SharedGuidebookResponse toPublicResponse(GuidebookShare share) {
        SharedGuidebookSnapshot snapshot = share.getSnapshot();
        SharedGuidebookResponse resp = new SharedGuidebookResponse();
        resp.setShareCode(share.getShareCode());
        resp.setTitle(share.getTitle());
        resp.setGuidebookVersion(share.getGuidebookVersion());
        resp.setSharedAt(snapshot.getSharedAt());
        resp.setExpiresAt(share.getExpiresAt());
        resp.setDays(snapshot.getDays());
        resp.setBudgetBreakdown(snapshot.getBudgetBreakdown());
        resp.setPackingList(snapshot.getPackingList());
        resp.setCulturalTips(snapshot.getCulturalTips());
        resp.setPitfallTips(snapshot.getPitfallTips());
        resp.setDisclaimer(snapshot.getDisclaimer());
        resp.setDegradedMode(snapshot.getDegradedMode());
        resp.setTravelDataAt(snapshot.getTravelDataAt());
        return resp;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
