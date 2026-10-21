package com.travel.modules.guidebook.companion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.travel.common.BizException;
import com.travel.common.ResultCode;
import com.travel.modules.chat.dto.IntentDto;
import com.travel.modules.guidebook.data.DataBlock;
import com.travel.modules.guidebook.data.TravelDataSnapshot;
import com.travel.modules.guidebook.data.TravelDataService;
import com.travel.modules.guidebook.dto.SuggestionOperation;
import com.travel.modules.guidebook.dto.TimeSlotDto;
import com.travel.modules.guidebook.entity.Guidebook;
import com.travel.modules.guidebook.entity.TripSuggestion;
import com.travel.modules.guidebook.mapper.GuidebookMapper;
import com.travel.modules.guidebook.mapper.TripSuggestionMapper;

@ExtendWith(MockitoExtension.class)
class TripCompanionScanServiceTest {

    private static final long GUIDEBOOK_ID = 5L;
    private static final long USER_ID = 7L;
    private static final String FETCHED_AT = "2026-10-21T07:00:00";

    @Mock
    private GuidebookMapper guidebookMapper;
    @Mock
    private TripSuggestionMapper suggestionMapper;
    @Mock
    private TravelDataService travelDataService;
    @Mock
    private SuggestionRuleEngine ruleEngine;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private TripCompanionScanService service;
    private TravelDataSnapshot snapshot;

    @BeforeEach
    void setUp() {
        service = new TripCompanionScanService(guidebookMapper, suggestionMapper,
                travelDataService, ruleEngine, redisTemplate);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        snapshot = new TravelDataSnapshot(FETCHED_AT,
                List.of(DataBlock.unavailable("weather", FETCHED_AT)));
    }

    @Test
    void scanAllPersistsDraftsForGuidebookInWindow() {
        Guidebook guidebook = guidebook(Guidebook.STATUS_CONFIRMED,
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(2));
        when(guidebookMapper.selectList(any())).thenReturn(List.of(guidebook));
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);
        when(travelDataService.fetch(any(IntentDto.class))).thenReturn(snapshot);
        when(ruleEngine.evaluate(eq(guidebook), eq(snapshot), any(LocalDate.class)))
                .thenReturn(List.of(draft()));
        when(suggestionMapper.insert(any(TripSuggestion.class))).thenReturn(1);

        int created = service.scanAll();

        assertThat(created).isEqualTo(1);
        ArgumentCaptor<TripSuggestion> captor = ArgumentCaptor.forClass(TripSuggestion.class);
        verify(suggestionMapper).insert(captor.capture());
        TripSuggestion saved = captor.getValue();
        assertThat(saved.getGuidebookId()).isEqualTo(GUIDEBOOK_ID);
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getStatus()).isEqualTo(TripSuggestion.STATUS_PENDING);
        assertThat(saved.getExpiresAt().toLocalTime()).isEqualTo(LocalTime.of(23, 59, 59));
        verify(redisTemplate).delete(anyString());
    }

    @Test
    void duplicateSuggestionIsNotCounted() {
        Guidebook guidebook = guidebook(Guidebook.STATUS_EXECUTING,
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(2));
        when(guidebookMapper.selectList(any())).thenReturn(List.of(guidebook));
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);
        when(travelDataService.fetch(any(IntentDto.class))).thenReturn(snapshot);
        when(ruleEngine.evaluate(eq(guidebook), eq(snapshot), any(LocalDate.class)))
                .thenReturn(List.of(draft()));
        when(suggestionMapper.insert(any(TripSuggestion.class)))
                .thenThrow(new DuplicateKeyException("duplicate"));

        assertThat(service.scanAll()).isZero();
    }

    @Test
    void skipsGuidebookWhenLockIsHeld() {
        Guidebook guidebook = guidebook(Guidebook.STATUS_EXECUTING,
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(2));
        when(guidebookMapper.selectList(any())).thenReturn(List.of(guidebook));
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(false);

        assertThat(service.scanAll()).isZero();
        verifyNoInteractions(travelDataService);
    }

    @Test
    void isolatesExceptionFromOneGuidebook() {
        Guidebook guidebook = guidebook(Guidebook.STATUS_EXECUTING,
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(2));
        when(guidebookMapper.selectList(any())).thenReturn(List.of(guidebook));
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);
        when(travelDataService.fetch(any(IntentDto.class)))
                .thenThrow(new IllegalStateException("boom"));

        assertThat(service.scanAll()).isZero();
        verify(redisTemplate).delete(anyString());
    }

    @Test
    void skipsGuidebookOutsideTravelWindow() {
        Guidebook guidebook = guidebook(Guidebook.STATUS_CONFIRMED,
                LocalDate.now().plusDays(2), LocalDate.now().plusDays(4));
        when(guidebookMapper.selectList(any())).thenReturn(List.of(guidebook));

        assertThat(service.scanAll()).isZero();
        verifyNoInteractions(travelDataService);
    }

    @Test
    void refreshRejectsForeignGuidebook() {
        Guidebook guidebook = guidebook(Guidebook.STATUS_EXECUTING,
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(2));
        guidebook.setUserId(USER_ID + 1);
        when(guidebookMapper.selectById(GUIDEBOOK_ID)).thenReturn(guidebook);

        assertThatThrownBy(() -> service.refresh(GUIDEBOOK_ID, USER_ID))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getResultCode()).isEqualTo(ResultCode.NOT_OWNER));
    }

    @Test
    void refreshRejectsMissingGuidebook() {
        when(guidebookMapper.selectById(GUIDEBOOK_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.refresh(GUIDEBOOK_ID, USER_ID))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getResultCode()).isEqualTo(ResultCode.NOT_FOUND));
    }

    private SuggestionDraft draft() {
        SuggestionOperation operation = new SuggestionOperation(
                SuggestionOperation.MOVE_NODE, "n1", 2,
                new TimeSlotDto("09:00", "10:30"), null);
        return new SuggestionDraft(TripSuggestion.TYPE_WEATHER,
                "weather_relocate|" + LocalDate.now() + "|2|n1",
                "今天有雨", "移到 Day2", List.of(operation), List.of(),
                Map.of("source", "weather"), LocalDate.now());
    }

    private Guidebook guidebook(String status, LocalDate start, LocalDate end) {
        Guidebook guidebook = new Guidebook();
        guidebook.setId(GUIDEBOOK_ID);
        guidebook.setUserId(USER_ID);
        guidebook.setStatus(status);
        IntentDto intent = new IntentDto();
        intent.setDestination("东京");
        IntentDto.DateWindow window = new IntentDto.DateWindow();
        window.setStart(start.toString());
        window.setEnd(end.toString());
        intent.setDateWindow(window);
        guidebook.setIntent(intent);
        return guidebook;
    }
}
