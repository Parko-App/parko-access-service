package com.parko.access.service.service;

import com.parko.access.service.dto.response.OccupancyResponse;
import com.parko.domain.lib.model.SessionStatus;
import com.parko.persistence.core.repository.ParkingSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OccupancyServiceTest {

    @Mock
    private ParkingSessionRepository parkingSessionRepository;

    private OccupancyService occupancyService;

    @BeforeEach
    void setUp() {
        occupancyService = new OccupancyService(parkingSessionRepository, 100);
    }

    @Test
    void getCurrent_countsActiveSessionsAgainstCapacity() {
        when(parkingSessionRepository.countByStatus(SessionStatus.ACTIVE)).thenReturn(42L);

        OccupancyResponse response = occupancyService.getCurrent();

        assertThat(response.occupied()).isEqualTo(42);
        assertThat(response.capacity()).isEqualTo(100);
        assertThat(response.available()).isEqualTo(58);
    }

    @Test
    void getCurrent_moreActiveThanCapacity_availableNeverNegative() {
        when(parkingSessionRepository.countByStatus(SessionStatus.ACTIVE)).thenReturn(120L);

        OccupancyResponse response = occupancyService.getCurrent();

        assertThat(response.occupied()).isEqualTo(120);
        assertThat(response.available()).isZero();
    }

    @Test
    void publishCurrent_withoutSubscribers_doesNotQueryDatabase() {
        occupancyService.publishCurrent();

        verify(parkingSessionRepository, never()).countByStatus(SessionStatus.ACTIVE);
    }

    @Test
    void publishCurrent_withSubscriber_recalculatesFromDatabase() {
        when(parkingSessionRepository.countByStatus(SessionStatus.ACTIVE)).thenReturn(10L);
        occupancyService.subscribe();

        occupancyService.publishCurrent();

        verify(parkingSessionRepository, times(2)).countByStatus(SessionStatus.ACTIVE);
    }
}
