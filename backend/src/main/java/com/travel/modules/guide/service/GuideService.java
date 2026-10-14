package com.travel.modules.guide.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.travel.common.PageResult;
import com.travel.modules.guide.dto.GuidePostItem;
import com.travel.modules.guide.entity.GuidePost;
import com.travel.modules.guide.mapper.GuidePostMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GuideService {

    private static final int MAX_PAGE_SIZE = 50;

    private final GuidePostMapper guidePostMapper;

    /** §5.7 推荐攻略流（分页）。pageSize 夹在 [1,50]，避免被用来拉全表。 */
    public PageResult<GuidePostItem> recommend(long page, long pageSize) {
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        IPage<GuidePost> p = guidePostMapper.selectPage(
                new Page<>(safePage, safeSize),
                Wrappers.<GuidePost>lambdaQuery()
                        .eq(GuidePost::getStatus, GuidePost.STATUS_PUBLISHED)
                        .orderByDesc(GuidePost::getSortWeight)
                        .orderByDesc(GuidePost::getId));
        List<GuidePostItem> items = p.getRecords().stream().map(this::toItem).toList();
        return PageResult.of(items, p.getCurrent(), p.getSize(), p.getTotal());
    }

    private GuidePostItem toItem(GuidePost g) {
        return new GuidePostItem(g.getId(), g.getTitle(), g.getDestination(), g.getCoverUrl(),
                g.getSummary(), g.getAuthorName(), g.getTags(), g.getLikeCount(), g.getViewCount(),
                g.getDurationDays(), g.getBudgetLevel());
    }
}
