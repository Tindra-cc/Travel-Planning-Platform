package com.travel.modules.guidebook.edit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.travel.modules.chat.dto.IntentDto;
import com.travel.modules.guidebook.dto.BudgetBreakdownDto;
import com.travel.modules.guidebook.dto.GuidebookDayDto;
import com.travel.modules.guidebook.dto.GuidebookNodeDto;

/**
 * 行程结构修复：压平同日时间重叠、把预算上限收束到意图预算内。
 * 生成校验、AI 调整、手动「一键修复」共用同一套规则，避免各入口行为不一致。
 */
public final class ItineraryRepairSupport {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final LocalTime DAY_END = LocalTime.of(23, 59);

    private ItineraryRepairSupport() {
    }

    /**
     * 修复全部日期与预算；营业时间类冲突无法自动修复，仍需人工处理。
     *
     * @param days   日程（原地修改）
     * @param budget 预算拆解（原地修改）
     * @param intent 旅行意图，提供预算上限
     * @return 是否发生了修改
     */
    public static boolean repair(List<GuidebookDayDto> days, BudgetBreakdownDto budget,
            IntentDto intent) {
        boolean changed = false;
        if (days != null) {
            for (GuidebookDayDto day : days) {
                changed |= resolveTimeOverlaps(day);
            }
        }
        changed |= clampBudget(budget, intent);
        return changed;
    }

    /**
     * 同日节点按开始时间压实：与前一节点重叠时整体后移，保持原时长。
     *
     * @param day 日程
     * @return 是否发生了修改
     */
    public static boolean resolveTimeOverlaps(GuidebookDayDto day) {
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
        if (!day.getNodes().equals(sorted)) {
            day.setNodes(sorted);
            changed = true;
        }
        return changed;
    }

    /**
     * 预算上限不得超出用户意图预算：先按比例缩放，再收敛舍入误差。
     * 即使所有条目本身没有区间（min == max），也会继续下压，保证总额不越界。
     *
     * @param budget 预算拆解
     * @param intent 旅行意图
     * @return 是否发生了修改
     */
    public static boolean clampBudget(BudgetBreakdownDto budget, IntentDto intent) {
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
        for (BudgetBreakdownDto.Item item : budget.getItems()) {
            BigDecimal min = item.getMin().multiply(factor).setScale(0, RoundingMode.HALF_UP);
            BigDecimal max = item.getMax().multiply(factor).setScale(0, RoundingMode.HALF_UP);
            if (min.compareTo(max) > 0) {
                min = max;
            }
            item.setMin(min);
            item.setMax(max);
        }

        // 第一轮：压缩各条目的 max-min 区间
        BigDecimal overflow = totalMax(budget).subtract(cap);
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

        // 第二轮：区间已压平仍超限时，整体等比下压，再逐单位消除余数
        if (totalMax(budget).compareTo(cap) > 0) {
            BigDecimal maxTotal = totalMax(budget);
            BigDecimal shrink = cap.divide(maxTotal, 6, RoundingMode.DOWN);
            for (BudgetBreakdownDto.Item item : budget.getItems()) {
                BigDecimal min = item.getMin().multiply(shrink).setScale(0, RoundingMode.DOWN);
                BigDecimal max = item.getMax().multiply(shrink).setScale(0, RoundingMode.DOWN);
                if (min.compareTo(max) > 0) {
                    min = max;
                }
                item.setMin(min);
                item.setMax(max);
            }
            int guard = 0;
            while (totalMax(budget).compareTo(cap) > 0 && guard++ < 10_000) {
                BudgetBreakdownDto.Item target = budget.getItems().stream()
                        .filter(item -> item.getMax().signum() > 0)
                        .max(Comparator.comparing(BudgetBreakdownDto.Item::getMax))
                        .orElse(null);
                if (target == null) {
                    break;
                }
                target.setMax(target.getMax().subtract(BigDecimal.ONE));
                if (target.getMin().compareTo(target.getMax()) > 0) {
                    target.setMin(target.getMax());
                }
            }
        }

        BigDecimal minTotal = budget.getItems().stream()
                .map(BudgetBreakdownDto.Item::getMin)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        budget.setTotal(new BudgetBreakdownDto.Total(minTotal, totalMax(budget)));
        return true;
    }

    private static BigDecimal totalMax(BudgetBreakdownDto budget) {
        return budget.getItems().stream()
                .map(BudgetBreakdownDto.Item::getMax)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static LocalTime parseTime(String value) {
        try {
            return LocalTime.parse(value, TIME_FORMAT);
        } catch (Exception e) {
            return null;
        }
    }
}
