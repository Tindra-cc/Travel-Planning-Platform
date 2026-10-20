package com.travel.modules.guidebook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.travel.modules.guidebook.data.DataBlock;
import com.travel.modules.guidebook.data.TravelDataSnapshot;
import com.travel.modules.guidebook.entity.Guidebook;
import com.travel.modules.guidebook.entity.GuidebookVersion;
import com.travel.modules.guidebook.mapper.GuidebookVersionMapper;

@ExtendWith(MockitoExtension.class)
class GuidebookVersionServiceTest {

    @Mock
    private GuidebookVersionMapper versionMapper;

    @Test
    void copiesTravelDataIntoSnapshot() {
        when(versionMapper.selectCount(any())).thenReturn(0L);
        TravelDataSnapshot snapshot = new TravelDataSnapshot("2026-10-20T10:00:00",
                List.of(DataBlock.ok("weather", "晴", null, "2026-10-20T10:00:00")));
        Guidebook guidebook = new Guidebook();
        guidebook.setId(5L);
        guidebook.setVersion(2);
        guidebook.setTravelData(snapshot);
        guidebook.setTravelDataAt(LocalDateTime.of(2026, 10, 20, 10, 0));

        new GuidebookVersionService(versionMapper).append(guidebook, "manual", "手动编辑", 1L);

        ArgumentCaptor<GuidebookVersion> captor = ArgumentCaptor.forClass(GuidebookVersion.class);
        verify(versionMapper).insert(captor.capture());
        assertThat(captor.getValue().getTravelData()).isEqualTo(snapshot);
        assertThat(captor.getValue().getTravelDataAt())
                .isEqualTo(LocalDateTime.of(2026, 10, 20, 10, 0));
    }

    @Test
    void skipsWhenVersionAlreadyExists() {
        when(versionMapper.selectCount(any())).thenReturn(1L);

        new GuidebookVersionService(versionMapper).append(new Guidebook(), "generate", "初次", 1L);

        verify(versionMapper, never()).insert(any());
    }
}
