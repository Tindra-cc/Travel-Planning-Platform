package com.travel.modules.guidebook.service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.travel.common.BizException;
import com.travel.common.PageResult;
import com.travel.common.ResultCode;
import com.travel.modules.guidebook.dto.GuidebookDayDto;
import com.travel.modules.guidebook.dto.GuidebookNodeDto;
import com.travel.modules.guidebook.dto.LinkValidateResponse;
import com.travel.modules.guidebook.dto.ResourceItemDto;
import com.travel.modules.guidebook.entity.ResourceSlot;
import com.travel.modules.guidebook.mapper.ResourceSlotMapper;

import lombok.RequiredArgsConstructor;

/**
 * 本地资源目录 + 签名转链。平台只做跳转，不生成订单、不处理资金。
 */
@Service
@RequiredArgsConstructor
public class ResourceService {

    public static final Set<String> TYPES =
            Set.of("flight", "hotel", "ticket", "car", "dining");
    /** 目的地没有专属资源时回退到该通用目录，保证任何行程都能演示绑定。 */
    private static final String GENERIC_DESTINATION = "*";
    /** 演示资源的供应商名带此标记，跳过时效校验，避免演示数据随时间失效。 */
    private static final String DEMO_SUPPLIER_MARK = "演示";
    private static final Duration FRESH_LIMIT = Duration.ofHours(48);

    private final ResourceSlotMapper resourceSlotMapper;
    private final LinkSignService linkSignService;

    public PageResult<ResourceItemDto> search(String type, String destination,
            BigDecimal budgetMin, BigDecimal budgetMax, int page, int pageSize) {
        if (!TYPES.contains(type)) {
            throw new BizException(ResultCode.PARAM_ERROR, "资源类型不合法");
        }
        if (!StringUtils.hasText(destination)) {
            throw new BizException(ResultCode.PARAM_ERROR, "destination 不能为空");
        }
        Page<ResourceSlot> result = resourceSlotMapper.selectPage(
                new Page<>(Math.max(1, page), Math.min(Math.max(1, pageSize), 100)),
                query(type, destination, budgetMin, budgetMax));
        // 目的地暂无专属资源时回退到通用目录，避免演示时绑定面板空白
        if (result.getTotal() == 0 && !GENERIC_DESTINATION.equals(destination)) {
            result = resourceSlotMapper.selectPage(
                    new Page<>(Math.max(1, page), Math.min(Math.max(1, pageSize), 100)),
                    query(type, GENERIC_DESTINATION, budgetMin, budgetMax));
        }
        List<ResourceItemDto> items = result.getRecords().stream().map(this::toItem).toList();
        return PageResult.of(items, result.getCurrent(), result.getSize(), result.getTotal());
    }

    private LambdaQueryWrapper<ResourceSlot> query(String type, String destination,
            BigDecimal budgetMin, BigDecimal budgetMax) {
        LambdaQueryWrapper<ResourceSlot> query = new LambdaQueryWrapper<ResourceSlot>()
                .eq(ResourceSlot::getType, type)
                .eq(ResourceSlot::getDestination, destination)
                .eq(ResourceSlot::getEnabled, true)
                .orderByDesc(ResourceSlot::getSortWeight);
        if (budgetMax != null) {
            query.le(ResourceSlot::getPriceMin, budgetMax);
        }
        if (budgetMin != null) {
            query.ge(ResourceSlot::getPriceMax, budgetMin);
        }
        return query;
    }

    public ResourceSlot requireEnabled(String resourceSlotId) {
        ResourceSlot slot = resourceSlotMapper.selectById(resourceSlotId);
        if (slot == null || Boolean.FALSE.equals(slot.getEnabled())) {
            throw new BizException(ResultCode.NOT_FOUND, "资源不存在或已下架");
        }
        return slot;
    }

    /** 详情/搜索统一从这里生成 bookingLink，前端不得自行拼接。 */
    public ResourceItemDto toItem(ResourceSlot slot) {
        return new ResourceItemDto(slot.getId(), slot.getType(), slot.getSupplier(),
                new ResourceItemDto.PriceRange(slot.getPriceMin(), slot.getPriceMax(),
                        slot.getCurrency()),
                slot.getAvailability(), slot.getRefundPolicy(),
                clickable(slot) ? bookingLink(slot) : null,
                slot.getFreshness());
    }

    public String bookingLink(ResourceSlot slot) {
        return "/api/link/click?rid=" + encode(slot.getId())
                + "&sig=" + linkSignService.sign(slot.getId());
    }

    public boolean clickable(ResourceSlot slot) {
        return Boolean.TRUE.equals(slot.getEnabled())
                && (ResourceSlot.AVAILABLE.equals(slot.getAvailability())
                        || ResourceSlot.TIGHT.equals(slot.getAvailability()))
                && fresh(slot)
                && validTargetUrl(slot.getTargetUrl());
    }

    /** 校验签名与资源状态后返回跳转地址；任何异常都不允许跳到非白名单 URL。 */
    public String resolveClick(String resourceSlotId, String signature) {
        ResourceSlot slot = resourceSlotMapper.selectById(resourceSlotId);
        if (slot == null || Boolean.FALSE.equals(slot.getEnabled())) {
            throw new BizException(ResultCode.NOT_FOUND, "资源不存在或已下架");
        }
        if (!linkSignService.verify(resourceSlotId, signature)) {
            throw new BizException(ResultCode.NOT_OWNER, "转链签名无效");
        }
        if (!clickable(slot)) {
            throw new BizException(ResultCode.CONFLICT, "资源当前不可跳转，请稍后重试");
        }
        return buildRedirectUrl(slot);
    }

    public LinkValidateResponse validate(List<String> resourceSlotIds) {
        if (resourceSlotIds == null || resourceSlotIds.isEmpty()) {
            throw new BizException(ResultCode.PARAM_ERROR, "resourceSlotIds 不能为空");
        }
        Set<String> ids = new LinkedHashSet<>(resourceSlotIds);
        Map<String, ResourceSlot> found = new LinkedHashMap<>();
        resourceSlotMapper.selectBatchIds(ids)
                .forEach(slot -> found.put(slot.getId(), slot));

        List<LinkValidateResponse.Result> results = new ArrayList<>();
        for (String id : ids) {
            ResourceSlot slot = found.get(id);
            if (slot == null || Boolean.FALSE.equals(slot.getEnabled())) {
                results.add(new LinkValidateResponse.Result(id, false, "not_found", null));
            } else if (!fresh(slot)) {
                results.add(new LinkValidateResponse.Result(id, false, "stale",
                        fallbackSupplier(slot)));
            } else if (!validTargetUrl(slot.getTargetUrl())) {
                results.add(new LinkValidateResponse.Result(id, false, "link_error",
                        fallbackSupplier(slot)));
            } else if (ResourceSlot.SOLD_OUT.equals(slot.getAvailability())) {
                results.add(new LinkValidateResponse.Result(id, false, "sold_out",
                        fallbackSupplier(slot)));
            } else {
                results.add(new LinkValidateResponse.Result(id, true, null, null));
            }
        }
        return new LinkValidateResponse(results);
    }

    /** 批量补充节点资源，避免逐节点查询。 */
    public void enrichNodes(List<GuidebookDayDto> days) {
        if (days == null) {
            return;
        }
        Set<String> ids = new LinkedHashSet<>();
        for (GuidebookDayDto day : days) {
            if (day == null || day.getNodes() == null) {
                continue;
            }
            for (GuidebookNodeDto node : day.getNodes()) {
                if (node != null && StringUtils.hasText(node.getResourceSlotId())) {
                    ids.add(node.getResourceSlotId());
                }
            }
        }
        Map<String, ResourceSlot> slots = new LinkedHashMap<>();
        if (!ids.isEmpty()) {
            resourceSlotMapper.selectBatchIds(ids)
                    .stream()
                    .filter(slot -> Boolean.TRUE.equals(slot.getEnabled()))
                    .forEach(slot -> slots.put(slot.getId(), slot));
        }
        for (GuidebookDayDto day : days) {
            if (day == null || day.getNodes() == null) {
                continue;
            }
            for (GuidebookNodeDto node : day.getNodes()) {
                if (node == null) {
                    continue;
                }
                ResourceSlot slot = node.getResourceSlotId() == null
                        ? null : slots.get(node.getResourceSlotId());
                node.setResource(slot == null ? null : toItem(slot));
            }
        }
    }

    private String fallbackSupplier(ResourceSlot slot) {
        List<ResourceSlot> candidates = resourceSlotMapper.selectList(
                new LambdaQueryWrapper<ResourceSlot>()
                        .eq(ResourceSlot::getType, slot.getType())
                        .eq(ResourceSlot::getDestination, slot.getDestination())
                        .eq(ResourceSlot::getEnabled, true)
                        .eq(ResourceSlot::getAvailability, ResourceSlot.AVAILABLE)
                        .ne(ResourceSlot::getId, slot.getId())
                        .orderByDesc(ResourceSlot::getSortWeight));
        for (ResourceSlot candidate : candidates) {
            if (fresh(candidate) && validTargetUrl(candidate.getTargetUrl())) {
                return candidate.getSupplier();
            }
        }
        return null;
    }

    private boolean fresh(ResourceSlot slot) {
        // 演示数据没有实时供应商刷新，按「…演示」后缀豁免时效，避免演示几天后全部不可点击
        if (slot.getSupplier() != null && slot.getSupplier().endsWith(DEMO_SUPPLIER_MARK)) {
            return true;
        }
        return slot.getFreshness() != null
                && slot.getFreshness().isAfter(LocalDateTime.now().minus(FRESH_LIMIT));
    }

    private boolean validTargetUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return false;
        }
        try {
            URI uri = URI.create(url);
            return "https".equalsIgnoreCase(uri.getScheme())
                    || "http".equalsIgnoreCase(uri.getScheme());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private String buildRedirectUrl(ResourceSlot slot) {
        StringBuilder url = new StringBuilder(slot.getTargetUrl());
        if (slot.getAffiliateParams() != null && !slot.getAffiliateParams().isEmpty()) {
            char separator = slot.getTargetUrl().contains("?") ? '&' : '?';
            for (Map.Entry<String, Object> entry : slot.getAffiliateParams().entrySet()) {
                url.append(separator).append(encode(entry.getKey())).append('=')
                        .append(encode(String.valueOf(entry.getValue())));
                separator = '&';
            }
        }
        return url.toString();
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    /** 供绑定校验：资源必须启用且可点击。 */
    public ResourceSlot requireBindable(String resourceSlotId) {
        ResourceSlot slot = requireEnabled(resourceSlotId);
        if (ResourceSlot.SOLD_OUT.equals(slot.getAvailability())
                || ResourceSlot.LINK_ERROR.equals(slot.getAvailability())) {
            throw new BizException(ResultCode.CONFLICT, "该资源当前不可预订，无法绑定");
        }
        return slot;
    }
}
