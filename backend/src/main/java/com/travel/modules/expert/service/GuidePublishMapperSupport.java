package com.travel.modules.expert.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.travel.modules.auth.entity.User;
import com.travel.modules.auth.mapper.UserMapper;
import com.travel.modules.guide.dto.GuidePlanItem;
import com.travel.modules.guide.dto.GuidePlanSection;
import com.travel.modules.guide.entity.GuidePost;
import com.travel.modules.guidebook.dto.GuidebookDayDto;
import com.travel.modules.guidebook.dto.GuidebookNodeDto;
import com.travel.modules.guidebook.entity.Guidebook;
import com.travel.modules.expert.dto.PublishRequest;
import com.travel.modules.expert.entity.GuidePublication;

import lombok.RequiredArgsConstructor;

/** 攻略 → 市场帖子的白名单映射：不复制画像、预算、引用与用户标识。 */
@Component
@RequiredArgsConstructor
public class GuidePublishMapperSupport {

    private final UserMapper userMapper;

    public GuidePost toPost(Guidebook guidebook, GuidePublication publication,
            PublishRequest request) {
        GuidePost post = new GuidePost();
        post.setTitle(request.getTitle().strip());
        post.setDestination(guidebook.getIntent() == null
                ? null : guidebook.getIntent().getDestination());
        post.setCoverUrl(trimToNull(request.getCoverUrl()));
        post.setSummary(StringUtils.hasText(request.getDescription())
                ? request.getDescription().strip()
                : defaultSummary(guidebook));
        post.setAuthorName(authorName(guidebook.getUserId()));
        post.setTags(request.getTags());
        post.setDurationDays(guidebook.getDays() == null ? 0 : guidebook.getDays().size());
        post.setPlanSections(toSections(guidebook.getDays()));
        post.setStatus(GuidePost.STATUS_PENDING_REVIEW);
        post.setReviewStatus(GuidePost.REVIEW_PENDING);
        post.setLikeCount(0);
        post.setViewCount(0);
        post.setSortWeight(0);
        post.setPublicationId(publication.getId());
        return post;
    }

    private List<GuidePlanSection> toSections(List<GuidebookDayDto> days) {
        if (days == null) {
            return List.of();
        }
        List<GuidePlanSection> sections = new ArrayList<>();
        for (GuidebookDayDto day : days) {
            if (day == null) {
                continue;
            }
            List<GuidePlanItem> items = new ArrayList<>();
            if (day.getNodes() != null) {
                for (GuidebookNodeDto node : day.getNodes()) {
                    if (node == null) {
                        continue;
                    }
                    String name = node.getPlace() == null ? null : node.getPlace().getName();
                    items.add(new GuidePlanItem(
                            StringUtils.hasText(name) ? name : node.getType(),
                            node.getNote(),
                            null));
                }
            }
            sections.add(new GuidePlanSection("spot", "Day " + day.getDay(), items));
        }
        return sections;
    }

    private String defaultSummary(Guidebook guidebook) {
        String destination = guidebook.getIntent() == null
                ? "旅行" : guidebook.getIntent().getDestination();
        int days = guidebook.getDays() == null ? 0 : guidebook.getDays().size();
        return destination + " " + days + " 天行程参考";
    }

    private String authorName(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return "旅行达人";
        }
        return StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getAccount();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
