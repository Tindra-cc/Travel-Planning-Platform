package com.travel.modules.guidebook.data;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

class OpeningHoursTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);
    private static final LocalDate SATURDAY = LocalDate.of(2026, 11, 7);

    @Test
    void parsesAlwaysOpen() {
        OpeningHours hours = OpeningHours.parse("24/7").orElseThrow();

        assertThat(hours.allows(MONDAY, LocalTime.of(23, 0), LocalTime.of(23, 59))).isTrue();
    }

    @Test
    void parsesWeeklyWindow() {
        OpeningHours hours = OpeningHours.parse("Mo-Su 09:00-17:00").orElseThrow();

        assertThat(hours.allows(MONDAY, LocalTime.of(10, 0), LocalTime.of(12, 0))).isTrue();
        assertThat(hours.allows(MONDAY, LocalTime.of(19, 0), LocalTime.of(20, 0))).isFalse();
    }

    @Test
    void respectsWeekdayRestrictions() {
        OpeningHours hours = OpeningHours.parse("Mo-Fr 09:00-17:00").orElseThrow();

        assertThat(hours.allows(MONDAY, LocalTime.of(10, 0), LocalTime.of(11, 0))).isTrue();
        assertThat(hours.allows(SATURDAY, LocalTime.of(10, 0), LocalTime.of(11, 0))).isFalse();
    }

    @Test
    void supportsSplitWindowsAndClosedDays() {
        OpeningHours hours = OpeningHours.parse("Mo-Sa 09:00-12:00,13:00-17:00; Su off")
                .orElseThrow();

        assertThat(hours.allows(MONDAY, LocalTime.of(9, 0), LocalTime.of(11, 0))).isTrue();
        assertThat(hours.allows(MONDAY, LocalTime.of(14, 0), LocalTime.of(16, 0))).isTrue();
        assertThat(hours.allows(LocalDate.of(2026, 11, 8), LocalTime.of(10, 0),
                LocalTime.of(11, 0))).isFalse();
    }

    @Test
    void rejectsUnparseableExpressions() {
        assertThat(OpeningHours.parse("sunrise-sunset")).isEmpty();
        assertThat(OpeningHours.parse("Mo-Fr")).isEmpty();
        assertThat(OpeningHours.parse("Mo-Fr 18:00-09:00")).isEmpty();
        assertThat(OpeningHours.parse("")).isEmpty();
        assertThat(OpeningHours.parse(null)).isEmpty();
    }

    @Test
    void nextWindowPullsEveningNodeIntoOpeningWindow() {
        OpeningHours hours = OpeningHours.parse("Mo-Su 09:00-17:00").orElseThrow();

        assertThat(hours.nextWindow(DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(19, 0)))
                .contains(new OpeningHours.Window(LocalTime.of(9, 0), LocalTime.of(10, 0)));
    }

    @Test
    void nextWindowShiftsEarlyNodeToOpeningTime() {
        OpeningHours hours = OpeningHours.parse("Mo-Su 09:00-17:00").orElseThrow();

        assertThat(hours.nextWindow(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(10, 0)))
                .contains(new OpeningHours.Window(LocalTime.of(9, 0), LocalTime.of(11, 0)));
    }

    @Test
    void nextWindowEmptyWhenDayIsClosed() {
        OpeningHours hours = OpeningHours.parse("Mo-Fr 09:00-17:00; Sa off").orElseThrow();

        assertThat(hours.nextWindow(DayOfWeek.SATURDAY, LocalTime.of(10, 0),
                LocalTime.of(11, 0))).isEmpty();
    }

    @Test
    void nextWindowEmptyWhenDurationDoesNotFit() {
        OpeningHours hours = OpeningHours.parse("Mo-Su 09:00-11:00").orElseThrow();

        assertThat(hours.nextWindow(DayOfWeek.MONDAY, LocalTime.of(12, 0),
                LocalTime.of(15, 0))).isEmpty();
    }

    @Test
    void treatsUnknownDateAsAllowed() {
        OpeningHours hours = OpeningHours.parse("Mo-Fr 09:00-17:00").orElseThrow();

        assertThat(hours.allows(null, LocalTime.of(22, 0), LocalTime.of(23, 0))).isTrue();
    }
}
