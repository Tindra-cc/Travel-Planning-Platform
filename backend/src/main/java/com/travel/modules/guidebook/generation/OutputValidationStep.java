package com.travel.modules.guidebook.generation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
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

import lombok.RequiredArgsConstructor;

/** Step 6：修复节点结构、时间格式、枚举与金额；无法修复时退回模板。 */
@Order(6)
@Component
@RequiredArgsConstructor
public class OutputValidationStep implements GenerationStep {

    private static final Set<String> TYPES = Set.of("activity", "meal", "transport", "stay", "free");
    private static final Pattern TIME = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final LocalTime DAY_END = LocalTime.of(23, 59);

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

        boolean repaired = false;
        int expected = PromptAssemblyStep.expectedDays(ctx.getIntent());
        for (int i = 0; i < days.size(); i++) {
            GuidebookDayDto day = days.get(i);
            if (day == null) {
                continue;
            }
            if (day.getDay() == null) {
                day.setDay(i + 1);
                repaired = true;
            }
            if (day.getNodes() == null) {
                day.setNodes(new ArrayList<>());
            }
            List<GuidebookNodeDto> nodes = day.getNodes();
            for (int j = 0; j < nodes.size(); j++) {
                repaired |= repairNode(nodes.get(j), day.getDay(), j + 1);
            }
            repaired |= resolveTimeOverlaps(day);
        }
        if (days.size() != expected) {
            ctx.addReason("day_count_mismatch");
        }
        if (!validBudget(ctx.getBudgetBreakdown())) {
            BudgetBreakdownDto templateBudget = templateComposer.compose(ctx).getBudgetBreakdown();
            validBudget(templateBudget);
            ctx.setBudgetBreakdown(templateBudget);
            repaired = true;
        }
        if (clampBudget(ctx.getBudgetBreakdown(), ctx.getIntent())) {
            repaired = true;
        }
        if (repaired) {
            ctx.addReason("validation_repaired");
        }
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

    /**
     * 同日节点按开始时间压实：与前一节点重叠时整体后移，保持原时长，确保生成结果不再报 time_overlap。
     */
    private boolean resolveTimeOverlaps(GuidebookDayDto day) {
        if (day == null || day.getNodes() == null || day.getNodes().size() < 2) {
            return false;
        }
        List<GuidebookNodeDto> sorted = new ArrayList<>(day.getNodes().stream()
                .filter(node -> node != null && node.getTimeSlot() != null)
                .filter(node -> {
                    LocalTime start = parseTime(node.getTimeSlot().getStart());
                    LocalTime end = parseTime(node.getTimeSlot().getEnd());
                    return start != null && end != null && end.isAfter(start);
                })
                .toList());
        sorted.sort(Comparator.comparing(node -> parseTime(node.getTimeSlot().getStart())));

        boolean changed = false;
        LocalTime cursor = null;
        for (GuidebookNodeDto node : sorted) {
            LocalTime start = parseTime(node.getTimeSlot().getStart());
            LocalTime end = parseTime(node.getTimeSlot().getEnd());
            long duration = Duration.between(start, end).toMinutes();
            LocalTime newStart = cursor != null && start.isBefore(cursor) ? cursor : start;
            long available = Duration.between(newStart, DAY_END).toMinutes();
            if (available < 1) {
                newStart = DAY_END.minusMinutes(1);
                available = 1;
            }
            LocalTime newEnd = newStart.plusMinutes(Math.min(duration, available));
            if (!newStart.equals(start) || !newEnd.equals(end)) {
                node.getTimeSlot().setStart(newStart.format(TIME_FORMAT));
                node.getTimeSlot().setEnd(newEnd.format(TIME_FORMAT));
                changed = true;
            }
            cursor = newEnd;
        }
        List<GuidebookNodeDto> original = day.getNodes();
        if (!original.equals(sorted)) {
            day.setNodes(sorted);
            changed = true;
        }
        return changed;
    }

    /** 预算上限不得超出用户意图预算；超出时按比例缩放并在舍入误差上做兜底扣减。 */
    private boolean clampBudget(BudgetBreakdownDto budget, com.travel.modules.chat.dto.IntentDto intent) {
        if (budget == null || budget.getTotal() == null || budget.getTotal().getMax() == null
                || budget.getItems() == null || budget.getItems().isEmpty()) {
            return false;
        }
        if (intent == null || intent.getBudget() == null || intent.getBudget().getAmount() == null) {
            return false;
        }
        BigDecimal cap = intent.getBudget().getAmount();
        if (cap.signum() <= 0 || budget.getTotal().getMax().compareTo(cap) <= 0) {
            return false;
        }
        String budgetCurrency = budget.getCurrency();
        String intentCurrency = intent.getBudget().getCurrency();
        if (budgetCurrency != null && intentCurrency != null
                && !budgetCurrency.equalsIgnoreCase(intentCurrency)) {
            return false;
        }

        BigDecimal factor = cap.divide(budget.getTotal().getMax(), 6, RoundingMode.HALF_UP);
        BigDecimal maxTotal = BigDecimal.ZERO;
        for (BudgetBreakdownDto.Item item : budget.getItems()) {
            BigDecimal min = item.getMin().multiply(factor).setScale(0, RoundingMode.HALF_UP);
            BigDecimal max = item.getMax().multiply(factor).setScale(0, RoundingMode.HALF_UP);
            if (min.compareTo(max) > 0) {
                min = max;
            }
            item.setMin(min);
            item.setMax(max);
            maxTotal = maxTotal.add(max);
        }
        BigDecimal overflow = maxTotal.subtract(cap);
        while (overflow.signum() > 0) {
            BudgetBreakdownDto.Item target = budget.getItems().stream()
                    .filter(item -> item.getMax().compareTo(item.getMin()) > 0)
                    .max(Comparator.comparing(BudgetBreakdownDto.Item::getMax))
                    .orElse(null);
            if (target == null) {
                break;
            }
            BigDecimal headroom = target.getMax().subtract(target.getMin());
            BigDecimal reduce = overflow.min(headroom);
            target.setMax(target.getMax().subtract(reduce));
            overflow = overflow.subtract(reduce);
        }
        BigDecimal minTotal = budget.getItems().stream()
                .map(BudgetBreakdownDto.Item::getMin)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal clampedMax = budget.getItems().stream()
                .map(BudgetBreakdownDto.Item::getMax)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        budget.setTotal(new BudgetBreakdownDto.Total(minTotal, clampedMax));
        return true;
    }

    private LocalTime parseTime(String value) {
        try {
            return LocalTime.parse(value, TIME_FORMAT);
        } catch (Exception e) {
            return null;
        }
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
