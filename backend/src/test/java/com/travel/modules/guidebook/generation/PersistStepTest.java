package com.travel.modules.guidebook.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.travel.modules.guidebook.data.DataBlock;
import com.travel.modules.guidebook.data.TravelDataSnapshot;
import com.travel.modules.guidebook.entity.Guidebook;
import com.travel.modules.guidebook.mapper.GuidebookMapper;
import com.travel.modules.guidebook.service.GuidebookVersionService;
import com.travel.support.TestFixtures;

@ExtendWith(MockitoExtension.class)
class PersistStepTest {

    @Mock
    private GuidebookMapper guidebookMapper;
    @Mock
    private GuidebookVersionService versionService;

    @Test
    void persistsTravelDataSnapshotAndAppendsVersion() {
        when(guidebookMapper.updateById(any())).thenReturn(1);
        when(guidebookMapper.selectById(1L)).thenReturn(new Guidebook());
        GenerationContext ctx = new GenerationContext(1L, 2L, 3L, TestFixtures.intent(), Map.of());
        ctx.setTitle("成都三日");
        ctx.setDays(TestFixtures.days());
        TravelDataSnapshot snapshot = new TravelDataSnapshot("2026-10-20T10:00:00",
                List.of(DataBlock.ok("weather", "晴", null, "2026-10-20T10:00:00")));
        ctx.setTravelData(snapshot);

        new PersistStep(guidebookMapper, versionService).execute(ctx);

        ArgumentCaptor<Guidebook> captor = ArgumentCaptor.forClass(Guidebook.class);
        verify(guidebookMapper).updateById(captor.capture());
        Guidebook saved = captor.getValue();
        assertThat(saved.getTravelData()).isEqualTo(snapshot);
        assertThat(saved.getTravelDataAt()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(Guidebook.STATUS_DRAFT);
        verify(versionService).append(any(), eq("generate"), eq("初次生成攻略"), eq(2L));
    }
}
