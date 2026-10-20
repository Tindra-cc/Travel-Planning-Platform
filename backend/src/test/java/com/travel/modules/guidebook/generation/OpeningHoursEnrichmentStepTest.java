package com.travel.modules.guidebook.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.travel.modules.chat.dto.IntentDto;
import com.travel.modules.guidebook.data.OsmOpeningHoursProvider;
import com.travel.modules.guidebook.dto.GuidebookDayDto;
import com.travel.modules.guidebook.dto.GuidebookNodeDto;
import com.travel.modules.guidebook.dto.PlaceDto;
import com.travel.modules.guidebook.dto.TimeSlotDto;
import com.travel.support.TestFixtures;

@ExtendWith(MockitoExtension.class)
class OpeningHoursEnrichmentStepTest {

    @Mock
    private OsmOpeningHoursProvider provider;

    @Test
    void enrichesActivityNodesAndLeavesOthersUntouched() {
        when(provider.lookup("成都", "武侯祠"))
                .thenReturn(Optional.of("Mo-Su 09:00-18:00"));
        OpeningHoursEnrichmentStep step = new OpeningHoursEnrichmentStep(provider);
        GenerationContext ctx = context(day(node("d1-1", "activity", "武侯祠"),
                node("d1-2", "meal", "火锅店")));

        step.execute(ctx);

        assertThat(ctx.getDays().get(0).getNodes().get(0).getOpeningHours())
                .isEqualTo("Mo-Su 09:00-18:00");
        assertThat(ctx.getDays().get(0).getNodes().get(0).getOpeningHoursSource()).isEqualTo("OSM");
        assertThat(ctx.getDays().get(0).getNodes().get(1).getOpeningHours()).isNull();
        verify(provider, never()).lookup(anyString(), org.mockito.ArgumentMatchers.eq("火锅店"));
    }

    @Test
    void capsLookupsPerDay() {
        when(provider.lookup(anyString(), anyString())).thenReturn(Optional.empty());
        OpeningHoursEnrichmentStep step = new OpeningHoursEnrichmentStep(provider);
        GuidebookDayDto day = day(
                node("d1-1", "activity", "A"), node("d1-2", "activity", "B"),
                node("d1-3", "activity", "C"), node("d1-4", "activity", "D"),
                node("d1-5", "activity", "E"));
        GenerationContext ctx = context(day);

        step.execute(ctx);

        verify(provider, times(4)).lookup(anyString(), anyString());
    }

    @Test
    void providerFailureDoesNotBreakGeneration() {
        when(provider.lookup(anyString(), anyString()))
                .thenThrow(new RuntimeException("overpass down"));
        OpeningHoursEnrichmentStep step = new OpeningHoursEnrichmentStep(provider);
        GenerationContext ctx = context(day(node("d1-1", "activity", "武侯祠")));

        assertThatCode(() -> step.execute(ctx)).doesNotThrowAnyException();
        assertThat(ctx.getDays().get(0).getNodes().get(0).getOpeningHours()).isNull();
    }

    private GenerationContext context(GuidebookDayDto... days) {
        IntentDto intent = TestFixtures.intent();
        GenerationContext ctx = new GenerationContext(1L, 2L, 3L, intent, Map.of());
        ctx.setDays(new ArrayList<>(List.of(days)));
        return ctx;
    }

    private GuidebookDayDto day(GuidebookNodeDto... nodes) {
        return new GuidebookDayDto(1, new ArrayList<>(List.of(nodes)));
    }

    private GuidebookNodeDto node(String id, String type, String place) {
        GuidebookNodeDto node = new GuidebookNodeDto();
        node.setNodeId(id);
        node.setType(type);
        node.setTimeSlot(new TimeSlotDto("09:00", "10:00"));
        node.setPlace(new PlaceDto(place, 30.66, 104.06));
        return node;
    }
}
