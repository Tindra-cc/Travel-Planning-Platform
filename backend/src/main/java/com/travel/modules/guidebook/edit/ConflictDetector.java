package com.travel.modules.guidebook.edit;

import java.math.BigDecimal;
import java.time.DateTimeException;
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
import com.travel.modules.guidebook.dto.NodeConflictDto;
import com.travel.modules.guidebook.entity.Guidebook;

public class ConflictDetector {

    public static final String TYPE_TIME_OVERLAP = "time_overlap";
    public static final String TYPE_UNREACHABLE = "unreachable";
    public static final String TYPE_BUDGET_EXCEEDED = "budget_exceeded";
    public static final String TYPE_CLOSED_TIME = "closed_time";

    private record TimedNode(GuidebookNodeDto node, LocalTime start,LocalTime end){

    }
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private static final long UNREACHABLE_GAP_MINUTES = 60;
    private static final double UNREACHABLE_DISTANCE_KM = 50.0;
    private static final double EARTH_RADIUS_KM = 6371.0;


    /**
     * 检测本攻略的冲突
     * @param guidebook
     * @return
     */
    public List<NodeConflictDto> detect(Guidebook guidebook){
        if (guidebook == null) {
            return List.of();
        }
        return detect(guidebook.getDays(),guidebook.getBudgetBreakdown(),guidebook.getIntent());
    }

    /**
     *纯数据入口，便于单测直接构造参数
     * @param days
     * @param budgetBreakdown
     * @param intent
     * @return
     */
    private List<NodeConflictDto> detect(List<GuidebookDayDto> days, BudgetBreakdownDto budgetBreakdown,
            IntentDto intent) {
        List<NodeConflictDto> conflicts = new ArrayList<>();
        if (days != null) {
            for(GuidebookDayDto day :days){
                checkDay(day, conflicts);
            }
        }
        checkBudget(budgetBreakdown, intent, conflicts);
        return conflicts;
    }

    /**
     * 检测单日内的时段重叠与不可达
     * @param day
     * @param conflicts
     */
    private void checkDay(GuidebookDayDto day, List<NodeConflictDto> conflicts){

        if (day == null || day.getNodes() == null || day.getNodes().size() < 2) {
            return;
        }

        List<TimedNode> timed = new ArrayList<>();
        for(GuidebookNodeDto node : day.getNodes()){
            TimedNode slot = toTimedNode(node);
            if (slot != null) {
                timed.add(slot);
            }
        }
        timed.sort(Comparator.comparing(TimedNode::start));

        for(int i = 1; i < timed.size(); i++){
            TimedNode prev = timed.get(i - 1);
            TimedNode curr = timed.get(i);

            if (curr.start().isBefore(prev.end())) {
                conflicts.add(new NodeConflictDto(TYPE_TIME_OVERLAP, day.getDay(),
                            List.of(prev.node().getNodeId(),curr.node().getNodeId()),
                            "「" + placeName(prev.node()) + "」(" + prev.start() + "-" + prev.end()
                            + ") 与「" + placeName(curr.node()) + "」(" + curr.start() + "-" + curr.end()
                            + ") 时段重叠"));
                            continue;
            }

            
        long gapMinutes = Duration.between(prev.end(),curr.start()).toMinutes();
        if (gapMinutes < UNREACHABLE_GAP_MINUTES) {
            Double km = distanceKm(prev.node(),curr.node());
            if (km != null && km > UNREACHABLE_DISTANCE_KM) {
                conflicts.add(new NodeConflictDto(TYPE_UNREACHABLE,day.getDay(),
                List.of(prev.node().getNodeId(),curr.node().getNodeId()),
            "「" + placeName(prev.node()) + "」到「" + placeName(curr.node())
            + "」相距约" + Math.round(km) + "公里，但只间隔" + gapMinutes + "分钟"));
            }
        }
        }
    }

    /**
     * 取地点名用于消息展示，缺失时给占位，避免用户看到 null。
     * @param node
     * @return
     */
    private String placeName(GuidebookNodeDto node) {
        return node.getPlace() != null && node.getPlace().getName() != null 
                && !node.getPlace().getName().isBlank()
                ? node.getPlace().getName()
                : "(未命名地点)";
    }

    /**
     *预算超限：用预算拆解的最高档与意图预算进行比较 取max是保守策略
     * @param budget
     * @param intent
     * @param conflicts
     */
    private void checkBudget(BudgetBreakdownDto budget,IntentDto intent,List<NodeConflictDto> conflicts){
        if (budget == null || budget.getTotal() == null || budget.getTotal().getMax() == null) {
            return;
        }
        if (intent == null || intent.getBudget() == null || intent.getBudget().getAmount() == null) {
            return;
        }
        String budgetCurrency = budget.getCurrency();
        String intentCurrency = intent.getBudget().getCurrency();

        if (budgetCurrency != null && intentCurrency != null && !budgetCurrency.equalsIgnoreCase(intentCurrency)) {
            return;
        }

        BigDecimal cap = intent.getBudget().getAmount();
        BigDecimal max = budget.getTotal().getMax();

        if (max.compareTo(cap) > 0) {
            conflicts.add(new NodeConflictDto(TYPE_BUDGET_EXCEEDED,null,List.of(),
            "预算上限：" + max + (budgetCurrency == null ? "" : " " + budgetCurrency) +
            "超出意图预算" + cap + (intentCurrency == null ? "" : " " + intentCurrency) ));
        }
    }

    /**
     * 用 Haversine 公式计算两个地点之间的球面距离。
     * @param a
     * @param b
     * @return
     */
    private Double distanceKm(GuidebookNodeDto a, GuidebookNodeDto b) {

        if (a.getPlace() == null || b.getPlace() == null 
            || a.getPlace().getLat() == null || a.getPlace().getLng() == null
            || b.getPlace().getLat() == null || b.getPlace().getLng() == null) {
                return  null;
        }

        double lat1 = Math.toRadians(a.getPlace().getLat());
        double lat2 = Math.toRadians(b.getPlace().getLat());
        double dLat = lat2 - lat1;
        double dLgn = Math.toRadians(b.getPlace().getLng() - a.getPlace().getLng());
        double h = Math.sin(dLat/2) * Math.sin(dLat/2)
                    + Math.cos(lat1) * Math.cos(lat2)
                    * Math.sin(dLgn/2) * Math.sin(dLgn/2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(h));
    }

    /**
     * 把节点转化成可比较的时间段
     * @param node
     * @return
     */
    private TimedNode toTimedNode(GuidebookNodeDto node){

        if (node == null || node.getTimeSlot() == null) {
            return null;
        }

        LocalTime start = parseTime(node.getTimeSlot().getStart());
        LocalTime end = parseTime(node.getTimeSlot().getEnd());
        if (start == null || end == null || !end.isAfter(start)) {
            return null;
        }

        return new TimedNode(node, start, end);
    }

    /**
     * 解析 HH:mm格式时间
     * @param value
     * @return
     */
    private LocalTime parseTime(String value){
        if (value == null || value.isBlank()) {
            return null;
        }

        try{
            return LocalTime.parse(value,TIME_FORMAT);
        }catch(DateTimeException e){
            return null;
        }
    }
}
