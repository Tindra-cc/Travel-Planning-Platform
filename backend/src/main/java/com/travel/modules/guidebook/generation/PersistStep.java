package com.travel.modules.guidebook.generation;

import java.time.LocalDateTime;
import java.util.ArrayList;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.travel.common.BizException;
import com.travel.common.ResultCode;
import com.travel.modules.guidebook.entity.Guidebook;
import com.travel.modules.guidebook.entity.GuidebookVersion;
import com.travel.modules.guidebook.mapper.GuidebookMapper;
import com.travel.modules.guidebook.service.GuidebookVersionService;

import lombok.RequiredArgsConstructor;

/** Step 9：把生成结果写回 t_guidebook，状态置为 draft。 */
@Order(9)
@Component
@RequiredArgsConstructor
public class PersistStep implements GenerationStep {

    private final GuidebookMapper guidebookMapper;
    private final GuidebookVersionService versionService;

    @Override
    public String name() {
        return "persist";
    }

    @Override
    public int progress() {
        return 100;
    }

    @Override
    @Transactional
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
        if (ctx.getTravelData() != null) {
            guidebook.setTravelData(ctx.getTravelData());
            guidebook.setTravelDataAt(LocalDateTime.now());
        }
        guidebook.setDegradedMode(ctx.degraded());
        guidebook.setDegradationReasons(new ArrayList<>(ctx.getDegradationReasons()));
        guidebook.setCurrentStep("completed");
        guidebook.setProgress(100);
        guidebook.setFinishedAt(LocalDateTime.now());
        if (guidebookMapper.updateById(guidebook) != 1) {
            throw new BizException(ResultCode.NOT_FOUND, "攻略不存在或已被删除");
        }
        Guidebook current = guidebookMapper.selectById(ctx.getGuidebookId());
        versionService.append(current,GuidebookVersion.CHANGE_TYPE_GENERATE,"初次生成攻略",ctx.getUserId());
    }

    private String truncate(String title) {
        if (title == null || title.isBlank()) {
            return "未命名攻略";
        }
        return title.length() > 128 ? title.substring(0, 128) : title;
    }
}
