package com.travel.modules.guidebook.generation;

import java.util.List;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.travel.modules.guidebook.data.OsmOpeningHoursProvider;
import com.travel.modules.guidebook.dto.GuidebookDayDto;
import com.travel.modules.guidebook.dto.GuidebookNodeDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Step 7：为已选出的活动地点补充 OSM 营业时间。依赖模型产出的地点名，所以只能在生成之后执行；
 * 查询失败保持 null，不影响攻略本身。
 */
@Slf4j
@Order(7)
@Component
@RequiredArgsConstructor
public class OpeningHoursEnrichmentStep implements GenerationStep {

    private static final int MAX_PLACES_PER_DAY = 4;
    private static final long TIME_BUDGET_MS = 12_000L;

    private final OsmOpeningHoursProvider provider;

    @Override
    public String name() {
        return "opening_hours_enrichment";
    }

    @Override
    public int progress() {
        return 85;
    }

    @Override
    public void execute(GenerationContext ctx) {
        List<GuidebookDayDto> days = ctx.getDays();
        if (days == null || ctx.getIntent() == null) {
            return;
        }
        String destination = ctx.getIntent().getDestination();
        long startedAt = System.currentTimeMillis();
        for (GuidebookDayDto day : days) {
            if (day == null || day.getNodes() == null) {
                continue;
            }
            int handled = 0;
            for (GuidebookNodeDto node : day.getNodes()) {
                if (handled >= MAX_PLACES_PER_DAY) {
                    break;
                }
                if (!eligible(node)) {
                    continue;
                }
                if (System.currentTimeMillis() - startedAt > TIME_BUDGET_MS) {
                    log.info("营业时间富化超出时间预算，后续地点跳过");
                    return;
                }
                handled++;
                try {
                    provider.lookup(destination, node.getPlace().getName()).ifPresent(hours -> {
                        node.setOpeningHours(hours);
                        node.setOpeningHoursSource("OSM");
                    });
                } catch (RuntimeException e) {
                    log.debug("营业时间富化失败 place={}: {}", node.getPlace().getName(),
                            e.getMessage());
                }
            }
        }
    }

    private boolean eligible(GuidebookNodeDto node) {
        return node != null
                && "activity".equals(node.getType())
                && node.getPlace() != null
                && StringUtils.hasText(node.getPlace().getName())
                && !StringUtils.hasText(node.getOpeningHours());
    }
}
