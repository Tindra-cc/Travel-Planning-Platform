package com.travel.modules.guidebook.generation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.travel.modules.guidebook.dto.BudgetBreakdownDto;
import com.travel.modules.guidebook.dto.GuidebookDayDto;
import com.travel.modules.guidebook.dto.GuidebookNodeDto;
import com.travel.modules.guidebook.dto.PlaceDto;
import com.travel.modules.guidebook.dto.TimeSlotDto;
import com.travel.modules.guidebook.edit.ItineraryRepairSupport;

import lombok.RequiredArgsConstructor;

/** Step 6：修复节点结构、时间格式、枚举与金额；无法修复时退回模板。 */
@Order(6)
@Component
@RequiredArgsConstructor
public class OutputValidationStep implements GenerationStep {

    private static final Set<String> TYPES = Set.of("activity", "meal", "transport", "stay", "free");
    private static final Pattern TIME = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");

    private final GuidebookTemplateComposer templateComposer;

    @Override
    public String name() {
        return "output_validation";
    }

    @Override
    public int progress() {
        return 80;
    }

    @Override
    public void execute(GenerationContext ctx) {
        List<GuidebookDayDto> days = ctx.getDays();
        if (!usable(days)) {
            ctx.addReason("validation_fallback");
            GeneratedContent template = templateComposer.compose(ctx);
            ctx.setTitle(template.getTitle());
            ctx.setDays(template.getDays());
            ctx.setBudgetBreakdown(template.getBudgetBreakdown());
            return;
        }

        int expected = PromptAssemblyStep.expectedDays(ctx.getIntent());
        for (int i = 0; i < days.size(); i++) {
            GuidebookDayDto day = days.get(i);
            if (day == null) {
                continue;
            }
            if (day.getDay() == null) {
                day.setDay(i + 1);
            }
            if (day.getNodes() == null) {
                day.setNodes(new ArrayList<>());
            }
            List<GuidebookNodeDto> nodes = day.getNodes();
            for (int j = 0; j < nodes.size(); j++) {
                repairNode(nodes.get(j), day.getDay(), j + 1);
            }
            ItineraryRepairSupport.resolveTimeOverlaps(day);
        }
        if (days.size() != expected) {
            ctx.addReason("day_count_mismatch");
        }
        if (!validBudget(ctx.getBudgetBreakdown())) {
            BudgetBreakdownDto templateBudget = templateComposer.compose(ctx).getBudgetBreakdown();
            validBudget(templateBudget);
            ctx.setBudgetBreakdown(templateBudget);
        }
        // 结构修复属于正常生成流程，不再计入用户可见的降级标记
        ItineraryRepairSupport.clampBudget(ctx.getBudgetBreakdown(), ctx.getIntent());
    }

    private boolean usable(List<GuidebookDayDto> days) {
        if (days == null || days.isEmpty()) {
            return false;
        }
        return days.stream().anyMatch(day -> day != null && day.getNodes() != null
                && day.getNodes().stream().anyMatch(node -> node != null));
    }

    private boolean repairNode(GuidebookNodeDto node, Integer day, int index) {
        if (node == null) {
            return false;
        }
        boolean repaired = false;
        if (node.getNodeId() == null || node.getNodeId().isBlank()) {
            node.setNodeId("d" + day + "-" + index);
            repaired = true;
        }
        if (node.getType() == null || !TYPES.contains(node.getType())) {
            node.setType("activity");
            repaired = true;
        }
        if (node.getTimeSlot() == null) {
            node.setTimeSlot(new TimeSlotDto("09:00", "10:00"));
            repaired = true;
        } else {
            if (!TIME.matcher(String.valueOf(node.getTimeSlot().getStart())).matches()) {
                node.getTimeSlot().setStart("09:00");
                repaired = true;
            }
            if (!TIME.matcher(String.valueOf(node.getTimeSlot().getEnd())).matches()) {
                node.getTimeSlot().setEnd("10:00");
                repaired = true;
            }
        }
        if (node.getPlace() == null || node.getPlace().getName() == null
                || node.getPlace().getName().isBlank()) {
            node.setPlace(new PlaceDto("待定地点", null, null));
            repaired = true;
        }
        if (node.getSource() == null || node.getSource().isBlank()) {
            node.setSource("通用知识");
            repaired = true;
        }
        return repaired;
    }

    private boolean validBudget(BudgetBreakdownDto budget) {
        if (budget == null || budget.getItems() == null || budget.getItems().isEmpty()
                || budget.getTotal() == null) {
            return false;
        }
        BigDecimal min = BigDecimal.ZERO;
        BigDecimal max = BigDecimal.ZERO;
        for (BudgetBreakdownDto.Item item : budget.getItems()) {
            if (item == null || item.getMin() == null || item.getMax() == null
                    || item.getMin().compareTo(item.getMax()) > 0) {
                return false;
            }
            min = min.add(item.getMin());
            max = max.add(item.getMax());
        }
        if (budget.getCurrency() == null || budget.getCurrency().isBlank()) {
            budget.setCurrency("CNY");
        }
        budget.setTotal(new BudgetBreakdownDto.Total(min, max));
        return true;
    }
}
