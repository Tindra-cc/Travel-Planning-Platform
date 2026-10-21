package com.travel.modules.expert.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.travel.common.UserContext;
import com.travel.modules.expert.dto.EarningItem;
import com.travel.modules.expert.dto.EarningsPageResponse;
import com.travel.modules.expert.entity.ExpertEarning;
import com.travel.modules.expert.entity.GuidePublication;
import com.travel.modules.expert.mapper.ExpertEarningMapper;
import com.travel.modules.expert.mapper.GuidePublicationMapper;

import lombok.RequiredArgsConstructor;

/** 收益只读投影：不计算、不汇总，表为空时返回空列表。 */
@Service
@RequiredArgsConstructor
public class ExpertEarningService {

    private static final int MAX_PAGE_SIZE = 50;

    private final ExpertEarningMapper earningMapper;
    private final GuidePublicationMapper publicationMapper;

    public EarningsPageResponse list(int page, int pageSize) {
        Long expertId = UserContext.getUserId();
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);

        Page<ExpertEarning> result = earningMapper.selectPage(
                new Page<>(safePage, safeSize),
                Wrappers.<ExpertEarning>lambdaQuery()
                        .eq(ExpertEarning::getExpertId, expertId)
                        .orderByDesc(ExpertEarning::getId));

        Map<Long, String> titles = loadTitles(result.getRecords());
        List<EarningItem> items = result.getRecords().stream()
                .map(row -> toItem(row, titles.get(row.getPublicationId())))
                .toList();

        EarningsPageResponse response = new EarningsPageResponse();
        response.setList(items);
        response.setPage(result.getCurrent());
        response.setPageSize(result.getSize());
        response.setTotal(result.getTotal());
        response.setSettlementAvailable(false);
        return response;
    }

    private Map<Long, String> loadTitles(List<ExpertEarning> rows) {
        Set<Long> ids = rows.stream()
                .map(ExpertEarning::getPublicationId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return publicationMapper.selectList(Wrappers.<GuidePublication>lambdaQuery()
                        .in(GuidePublication::getId, ids))
                .stream()
                .collect(Collectors.toMap(GuidePublication::getId,
                        GuidePublication::getTitle, (a, b) -> a));
    }

    private EarningItem toItem(ExpertEarning row, String title) {
        return new EarningItem(String.valueOf(row.getId()),
                row.getPublicationId() == null ? null : String.valueOf(row.getPublicationId()),
                title == null ? "已下架的发布记录" : title,
                row.getAmountCents(), row.getStatus(), row.getCreateTime());
    }
}
