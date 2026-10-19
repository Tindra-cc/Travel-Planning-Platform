package com.travel.modules.guidebook.generation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.travel.modules.guidebook.dto.BudgetBreakdownDto;
import com.travel.modules.guidebook.dto.GuidebookDayDto;
import com.travel.modules.guidebook.dto.GuidebookNodeDto;
import com.travel.modules.guidebook.dto.PlaceDto;
import com.travel.modules.guidebook.dto.TimeSlotDto;
import com.travel.modules.guidebook.knowledge.KnowledgeSnippet;

/**
 * 无 AI 可用时的骨架模板：保证任何环境下都能产出 draft，
 * 内容明确标注“模板生成”，不冒充 AI 结果。
 */
@Component
public class GuidebookTemplateComposer {

    private static final String[] RATIOS = {"0.30", "0.30", "0.20", "0.12", "0.08"};
    private static final String[] CATEGORIES = {"交通", "住宿", "餐饮", "门票活动", "其他"};

    public GeneratedContent compose(GenerationContext ctx) {
        GeneratedContent content = new GeneratedContent();
        String destination = ctx.getIntent().getDestination();
        int days = PromptAssemblyStep.expectedDays(ctx.getIntent());
        content.setTitle(destination + days + "日行程（模板生成）");
        content.setDays(buildDays(ctx, days));
        content.setBudgetBreakdown(buildBudget(ctx));
        content.setPackingList(defaultPacking());
        content.setCulturalTips(List.of(
                "模板生成：建议出发前确认当地礼仪与节假日安排。",
                "进入宗教场所请注意着装与拍照规定。"));
        content.setPitfallTips(List.of(
                "模板生成：热门景点建议提前预约并避开正午高峰。",
                "实时天气与汇率数据可能缺失，出行前请再次核实。"));
        return content;
    }

    private List<GuidebookDayDto> buildDays(GenerationContext ctx, int days) {
        String destination = ctx.getIntent().getDestination();
        List<String> places = ctx.getKnowledge().stream()
                .map(KnowledgeSnippet::title)
                .filter(title -> title != null && !title.isBlank())
                .toList();
        List<GuidebookDayDto> result = new ArrayList<>();
        for (int day = 1; day <= days; day++) {
            String first = pick(places, (day - 1) * 2, destination + "核心景点");
            String second = pick(places, (day - 1) * 2 + 1, destination + "特色街区");
            List<GuidebookNodeDto> nodes = List.of(
                    node(day, 1, "09:00", "11:30", "activity", first, "模板生成的上午行程"),
                    node(day, 2, "12:00", "13:00", "meal", destination + "本地餐厅",
                            "模板生成的午餐安排"),
                    node(day, 3, "14:00", "17:00", "activity", second, "模板生成的下午行程"),
                    node(day, 4, "18:00", "19:30", "meal", destination + "特色餐饮",
                            "模板生成的晚餐安排"));
            result.add(new GuidebookDayDto(day, nodes));
        }
        return result;
    }

    private GuidebookNodeDto node(int day, int index, String start, String end,
            String type, String placeName, String note) {
        GuidebookNodeDto node = new GuidebookNodeDto();
        node.setNodeId("d" + day + "-" + index);
        node.setTimeSlot(new TimeSlotDto(start, end));
        node.setType(type);
        node.setPlace(new PlaceDto(placeName, null, null));
        node.setSource("模板生成（AI 不可用）");
        node.setNote(note);
        return node;
    }

    private String pick(List<String> places, int index, String fallback) {
        if (places.isEmpty()) {
            return fallback;
        }
        return places.get(index % places.size());
    }

    private BudgetBreakdownDto buildBudget(GenerationContext ctx) {
        BigDecimal amount = ctx.getIntent().getBudget() == null
                || ctx.getIntent().getBudget().getAmount() == null
                ? new BigDecimal("3000")
                : ctx.getIntent().getBudget().getAmount();
        String currency = ctx.getIntent().getBudget() == null
                || ctx.getIntent().getBudget().getCurrency() == null
                ? "CNY"
                : ctx.getIntent().getBudget().getCurrency();

        List<BudgetBreakdownDto.Item> items = new ArrayList<>();
        BigDecimal totalMin = BigDecimal.ZERO;
        BigDecimal totalMax = BigDecimal.ZERO;
        for (int i = 0; i < CATEGORIES.length; i++) {
            BigDecimal ratio = new BigDecimal(RATIOS[i]);
            BigDecimal base = amount.multiply(ratio);
            BigDecimal min = base.multiply(new BigDecimal("0.85")).setScale(0, RoundingMode.HALF_UP);
            BigDecimal max = base.multiply(new BigDecimal("1.15")).setScale(0, RoundingMode.HALF_UP);
            totalMin = totalMin.add(min);
            totalMax = totalMax.add(max);
            items.add(new BudgetBreakdownDto.Item(CATEGORIES[i], min, max));
        }
        return new BudgetBreakdownDto(currency, items,
                new BudgetBreakdownDto.Total(totalMin, totalMax));
    }

    private List<String> defaultPacking() {
        return List.of("身份证件", "充电设备", "常用药品", "换洗衣物", "雨具");
    }
}
