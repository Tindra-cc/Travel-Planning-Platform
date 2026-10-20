package com.travel.support;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.travel.modules.auth.entity.User;
import com.travel.modules.chat.dto.IntentDto;
import com.travel.modules.chat.entity.ChatSession;
import com.travel.modules.guidebook.dto.BudgetBreakdownDto;
import com.travel.modules.guidebook.dto.GuidebookDayDto;
import com.travel.modules.guidebook.dto.GuidebookNodeDto;
import com.travel.modules.guidebook.dto.PlaceDto;
import com.travel.modules.guidebook.dto.TimeSlotDto;
import com.travel.modules.guidebook.entity.Guidebook;

/** 测试数据构造器，保持各测试类的输入口径一致。 */
public final class TestFixtures {

    private TestFixtures() {
    }

    public static IntentDto intent() {
        IntentDto intent = new IntentDto();
        intent.setDestination("成都");
        IntentDto.DateWindow window = new IntentDto.DateWindow();
        window.setStart("2026-11-01");
        window.setEnd("2026-11-02");
        intent.setDateWindow(window);
        IntentDto.Party party = new IntentDto.Party();
        party.setAdults(2);
        intent.setParty(party);
        IntentDto.Budget budget = new IntentDto.Budget();
        budget.setAmount(new BigDecimal("3000"));
        budget.setCurrency("CNY");
        intent.setBudget(budget);
        intent.setPreferences(List.of("美食", "人文"));
        return intent;
    }

    public static User user(long id, String status) {
        User user = new User();
        user.setId(id);
        user.setAccount("user" + id + "@test.local");
        user.setAccountType("email");
        user.setNickname("用户" + id);
        user.setUserLevel("L1");
        user.setStatus(1);
        user.setDailyGenerateQuota(3);
        user.setDailyGenerateUsed(0);
        user.setGenerateQuotaDate(LocalDate.now());
        user.setProfileCompleted(false);
        return user;
    }

    public static ChatSession session(long id, long userId, String status) {
        ChatSession session = new ChatSession();
        session.setId(id);
        session.setUserId(userId);
        session.setTitle("成都两日");
        session.setStatus(status);
        session.setIntent(intent());
        return session;
    }

    public static Guidebook guidebook(long id, long userId, String status) {
        Guidebook guidebook = new Guidebook();
        guidebook.setId(id);
        guidebook.setUserId(userId);
        guidebook.setStatus(status);
        guidebook.setVersion(1);
        guidebook.setTitle("成都两日攻略");
        guidebook.setIntent(intent());
        guidebook.setDays(days());
        guidebook.setBudgetBreakdown(budget());
        guidebook.setAttemptCount(1);
        return guidebook;
    }

    public static List<GuidebookDayDto> days() {
        List<GuidebookNodeDto> nodes = new ArrayList<>();
        nodes.add(node("d1-1", "09:00", "11:00", "宽窄巷子"));
        nodes.add(node("d1-2", "12:00", "13:00", "奎星楼街"));
        List<GuidebookDayDto> days = new ArrayList<>();
        days.add(new GuidebookDayDto(1, nodes));
        days.add(new GuidebookDayDto(2, new ArrayList<>()));
        return days;
    }

    public static GuidebookNodeDto node(String id, String start, String end, String place) {
        return new GuidebookNodeDto(id, new TimeSlotDto(start, end), "activity",
                new PlaceDto(place, 30.66, 104.06), null, "[K1] 内置知识", null, null, null,
                null, null, "测试节点");
    }

    public static BudgetBreakdownDto budget() {
        List<BudgetBreakdownDto.Item> items = List.of(
                new BudgetBreakdownDto.Item("交通", new BigDecimal("200"),
                        new BigDecimal("400")),
                new BudgetBreakdownDto.Item("餐饮", new BigDecimal("300"),
                        new BigDecimal("600")));
        return new BudgetBreakdownDto("CNY", items,
                new BudgetBreakdownDto.Total(new BigDecimal("500"), new BigDecimal("1000")));
    }
}
