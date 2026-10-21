package com.travel.modules.guidebook.companion;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TripCompanionSchedulerTest {

    @Mock
    private TripCompanionScanService scanService;

    @Test
    void delegatesToScanService() {
        new TripCompanionScheduler(scanService).scan();

        verify(scanService).scanAll();
    }

    @Test
    void doesNotPropagateScanFailure() {
        when(scanService.scanAll()).thenThrow(new IllegalStateException("boom"));

        assertThatCode(() -> new TripCompanionScheduler(scanService).scan())
                .doesNotThrowAnyException();
    }
}
