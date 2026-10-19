package com.travel.modules.guidebook.generation;

import java.time.LocalDateTime;
import java.util.ArrayList;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.travel.common.BizException;
import com.travel.common.ResultCode;
import com.travel.modules.guidebook.entity.Guidebook;
import com.travel.modules.guidebook.mapper.GuidebookMapper;

import lombok.RequiredArgsConstructor;

/** Step 8：把生成结果写回 t_guidebook，状态置为 draft。 */
@Order(8)
@Component
@RequiredArgsConstructor
public class PersistStep implements GenerationStep {

    private final GuidebookMapper guidebookMapper;

    @Override
    public String name() {
        return "persist";
    }

    @Override
    public int progress() {
        return 100;
    }

    @Override
    public void execute(GenerationContext ctx) {
        Guidebook guidebook = new Guidebook();
        guidebook.setId(ctx.getGuidebookId());
        guidebook.setStatus(Guidebook.STATUS_DRAFT);
        guidebook.setTitle(truncate(ctx.getTitle()));
        guidebook.setDays(ctx.getDays());
        guidebook.setBudgetBreakdown(ctx.getBudgetBreakdown());
        guidebook.setPackingList(ctx.getPackingList());
        guidebook.setCulturalTips(ctx.getCulturalTips());
        guidebook.setPitfallTips(ctx.getPitfallTips());
        guidebook.setDegradedMode(ctx.degraded());
        guidebook.setDegradationReasons(new ArrayList<>(ctx.getDegradationReasons()));
        guidebook.setCurrentStep("completed");
        guidebook.setProgress(100);
        guidebook.setFinishedAt(LocalDateTime.now());
        if (guidebookMapper.updateById(guidebook) != 1) {
            throw new BizException(ResultCode.NOT_FOUND, "攻略不存在或已被删除");
        }
    }

    private String truncate(String title) {
        if (title == null || title.isBlank()) {
            return "未命名攻略";
        }
        return title.length() > 128 ? title.substring(0, 128) : title;
    }
}
