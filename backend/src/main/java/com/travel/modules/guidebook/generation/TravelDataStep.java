package com.travel.modules.guidebook.generation;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.travel.modules.guidebook.data.TravelDataService;
import com.travel.modules.guidebook.data.TravelDataSnapshot;

import lombok.RequiredArgsConstructor;

/** Step 3：天气/汇率实时数据；任一缺失只记录原因，继续生成。 */
@Order(3)
@Component
@RequiredArgsConstructor
public class TravelDataStep implements GenerationStep {

    private final TravelDataService travelDataService;

    @Override
    public String name() {
        return "travel_data";
    }

    @Override
    public int progress() {
        return 25;
    }

    @Override
    public void execute(GenerationContext ctx) {
        TravelDataSnapshot snapshot = travelDataService.fetch(ctx.getIntent());
        ctx.setTravelData(snapshot);
        if (snapshot != null) {
            ctx.getDegradationReasons().addAll(snapshot.unavailable());
        }
    }
}
