package com.travel.modules.guidebook.generation;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.travel.modules.guidebook.dto.GuidebookDayDto;
import com.travel.modules.guidebook.dto.GuidebookNodeDto;

/** Step 7：给每个节点补充来源兜底与实时数据采集时间。 */
@Order(7)
@Component
public class TraceabilityStep implements GenerationStep {

    @Override
    public String name() {
        return "traceability";
    }

    @Override
    public int progress() {
        return 90;
    }

    @Override
    public void execute(GenerationContext ctx) {
        String freshness = ctx.getTravelData() == null ? null : ctx.getTravelData().fetchedAt();
        if (ctx.getDays() == null) {
            return;
        }
        for (GuidebookDayDto day : ctx.getDays()) {
            if (day == null || day.getNodes() == null) {
                continue;
            }
            for (GuidebookNodeDto node : day.getNodes()) {
                if (node == null) {
                    continue;
                }
                if (node.getSource() == null || node.getSource().isBlank()) {
                    node.setSource("通用知识");
                }
                if (freshness != null && node.getDataFreshness() == null) {
                    node.setDataFreshness(freshness);
                }
            }
        }
    }
}
