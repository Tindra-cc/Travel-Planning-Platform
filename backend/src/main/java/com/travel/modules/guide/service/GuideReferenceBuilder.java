package com.travel.modules.guide.service;

import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.modules.guide.dto.GuidePlanItem;
import com.travel.modules.guide.dto.GuidePlanSection;
import com.travel.modules.guide.entity.GuidePost;
import com.travel.modules.guide.mapper.GuidePostMapper;
import com.travel.modules.guidebook.dto.ReferenceGuidebook;

import lombok.RequiredArgsConstructor;

/**
 * 把用户选中的推荐攻略转成模型可读的参考上下文。
 * 对话每轮都会带上这段上下文，生成攻略时再作为 FR-12 同款「参考攻略」注入提示词。
 */
@Component
@RequiredArgsConstructor
public class GuideReferenceBuilder {

    private static final int MAX_SECTIONS = 6;
    private static final int MAX_ITEMS_PER_SECTION = 5;
    private static final int MAX_TEXT_CHARS = 4_000;

    private final GuidePostMapper guidePostMapper;
    private final ObjectMapper objectMapper;

    /** 返回已发布的推荐攻略；不存在或已下架返回 null。 */
    public GuidePost published(Long postId) {
        if (postId == null) {
            return null;
        }
        GuidePost post = guidePostMapper.selectById(postId);
        return post != null && GuidePost.STATUS_PUBLISHED.equals(post.getStatus()) ? post : null;
    }

    /** 对话用：一段纯文本参考上下文；攻略不存在时返回 null。 */
    public String contextText(Long postId) {
        GuidePost post = published(postId);
        return post == null ? null : buildText(post);
    }

    /** 生成用：裁剪成提示词参考骨架；攻略不存在时返回 null。 */
    public ReferenceGuidebook toReference(Long postId) {
        GuidePost post = published(postId);
        if (post == null) {
            return null;
        }
        ReferenceGuidebook ref = new ReferenceGuidebook();
        ref.setTitle(post.getTitle());
        ref.setRawText(buildText(post));
        return ref;
    }

    private String buildText(GuidePost post) {
        StringBuilder sb = new StringBuilder();
        sb.append("【用户选中的推荐攻略】\n");
        sb.append("标题：").append(nullToEmpty(post.getTitle())).append('\n');
        if (post.getDestination() != null) {
            sb.append("目的地：").append(post.getDestination()).append('\n');
        }
        if (post.getDurationDays() != null) {
            sb.append("建议天数：").append(post.getDurationDays()).append(" 天\n");
        }
        if (post.getBudgetLevel() != null) {
            sb.append("预算档位：").append(post.getBudgetLevel()).append('\n');
        }
        if (post.getTags() != null && !post.getTags().isEmpty()) {
            sb.append("标签：").append(String.join("、", post.getTags())).append('\n');
        }
        if (post.getSummary() != null) {
            sb.append("摘要：").append(post.getSummary()).append('\n');
        }

        List<GuidePlanSection> sections = planSections(post);
        if (!sections.isEmpty()) {
            sb.append("规划参考：\n");
            int sectionCount = 0;
            for (GuidePlanSection section : sections) {
                if (section == null || sectionCount++ >= MAX_SECTIONS) {
                    continue;
                }
                sb.append("- ").append(nullToEmpty(section.getTitle())).append('：');
                if (section.getItems() == null || section.getItems().isEmpty()) {
                    sb.append("（无条目）\n");
                    continue;
                }
                int itemCount = 0;
                for (GuidePlanItem item : section.getItems()) {
                    if (item == null || itemCount++ >= MAX_ITEMS_PER_SECTION) {
                        continue;
                    }
                    sb.append(item.getName());
                    if (item.getPrice() != null) {
                        sb.append('（').append(item.getPrice()).append('）');
                    }
                    if (item.getNote() != null) {
                        sb.append(' ').append(item.getNote());
                    }
                    sb.append("；");
                }
                sb.append('\n');
            }
        }

        sb.append("使用要求：这是用户主动选择的参考，请围绕它的目的地、天数和玩法风格理解用户需求；")
                .append("参考中的价格与营业时间视为历史信息，不要当作实时数据复述或编造新价格。");

        String text = sb.toString();
        return text.length() > MAX_TEXT_CHARS ? text.substring(0, MAX_TEXT_CHARS) : text;
    }

    private List<GuidePlanSection> planSections(GuidePost post) {
        if (post.getPlanSections() == null || post.getPlanSections().isEmpty()) {
            return List.of();
        }
        try {
            return objectMapper.convertValue(post.getPlanSections(),
                    new TypeReference<List<GuidePlanSection>>() {});
        } catch (IllegalArgumentException e) {
            return List.of();
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
