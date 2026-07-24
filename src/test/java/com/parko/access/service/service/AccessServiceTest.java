package com.parko.access.service.service;

import com.parko.access.service.dto.request.AccessRequest;
import com.parko.access.service.dto.response.AccessResponse;
import com.parko.access.service.strategy.AccessDecision;
import com.parko.access.service.strategy.AccessResolutionStrategy;
import com.parko.domain.lib.model.AccessEventType;
import com.parko.domain.lib.model.AccessMethod;
import com.parko.domain.lib.model.AccessResult;
import com.parko.persistence.core.model.entity.AccessLogEntity;
import com.parko.persistence.core.repository.AccessLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessServiceTest {

    @Mock
    private AccessResolutionStrategy plateStrategy;

    @Mock
    private AccessLogRepository accessLogRepository;

    private AccessService accessService;

    @BeforeEach
    void setUp() {
        when(plateStrategy.supports()).thenReturn(AccessMethod.PLATE);
        accessService = new AccessService(List.of(plateStrategy), accessLogRepository);
    }

    @Test
    void resolveAccess_authorizedEntry_savesAccessLogAndReturnsResponse() {
        UUID sessionId = UUID.randomUUID();
        AccessDecision decision = new AccessDecision(AccessResult.AUTHORIZED, AccessEventType.ENTRY, sessionId, null);
        AccessRequest request = new AccessRequest(AccessMethod.PLATE, "AB123CD", "device-1", null);
        when(plateStrategy.resolve(request)).thenReturn(decision);
        when(accessLogRepository.save(any(AccessLogEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        AccessResponse response = accessService.resolveAccess(request);

        assertThat(response.result()).isEqualTo(AccessResult.AUTHORIZED);
        assertThat(response.eventType()).isEqualTo(AccessEventType.ENTRY);

        ArgumentCaptor<AccessLogEntity> captor = ArgumentCaptor.forClass(AccessLogEntity.class);
        verify(accessLogRepository).save(captor.capture());
        assertThat(captor.getValue().getAccessMethod()).isEqualTo(AccessMethod.PLATE);
        assertThat(captor.getValue().getResult()).isEqualTo(AccessResult.AUTHORIZED);
        assertThat(captor.getValue().getEventType()).isEqualTo(AccessEventType.ENTRY);
        assertThat(captor.getValue().getParkingSession().getId()).isEqualTo(sessionId);
    }

    @Test
    void resolveAccess_deniedWithoutSession_savesAccessLogWithNullParkingSession() {
        AccessDecision decision = new AccessDecision(AccessResult.DENIED, AccessEventType.ENTRY, null, "Patente no registrada");
        AccessRequest request = new AccessRequest(AccessMethod.PLATE, "ZZ999ZZ", "device-1", null);
        when(plateStrategy.resolve(request)).thenReturn(decision);
        when(accessLogRepository.save(any(AccessLogEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        AccessResponse response = accessService.resolveAccess(request);

        assertThat(response.result()).isEqualTo(AccessResult.DENIED);
        assertThat(response.message()).isEqualTo("Patente no registrada");

        ArgumentCaptor<AccessLogEntity> captor = ArgumentCaptor.forClass(AccessLogEntity.class);
        verify(accessLogRepository).save(captor.capture());
        assertThat(captor.getValue().getParkingSession()).isNull();
    }

    @Test
    void resolveAccess_occurredAtNull_defaultsToNow() {
        AccessDecision decision = new AccessDecision(AccessResult.AUTHORIZED, AccessEventType.ENTRY, UUID.randomUUID(), null);
        AccessRequest request = new AccessRequest(AccessMethod.PLATE, "AB123CD", "device-1", null);
        when(plateStrategy.resolve(request)).thenReturn(decision);
        when(accessLogRepository.save(any(AccessLogEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        LocalDateTime before = LocalDateTime.now();
        accessService.resolveAccess(request);
        LocalDateTime after = LocalDateTime.now();

        ArgumentCaptor<AccessLogEntity> captor = ArgumentCaptor.forClass(AccessLogEntity.class);
        verify(accessLogRepository).save(captor.capture());
        assertThat(captor.getValue().getOccurredAt()).isBetween(before, after);
    }

    @Test
    void resolveAccess_occurredAtProvided_isPreserved() {
        LocalDateTime occurredAt = LocalDateTime.now().minusMinutes(5);
        AccessDecision decision = new AccessDecision(AccessResult.AUTHORIZED, AccessEventType.ENTRY, UUID.randomUUID(), null);
        AccessRequest request = new AccessRequest(AccessMethod.PLATE, "AB123CD", "device-1", occurredAt);
        when(plateStrategy.resolve(request)).thenReturn(decision);
        when(accessLogRepository.save(any(AccessLogEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        accessService.resolveAccess(request);

        ArgumentCaptor<AccessLogEntity> captor = ArgumentCaptor.forClass(AccessLogEntity.class);
        verify(accessLogRepository).save(captor.capture());
        assertThat(captor.getValue().getOccurredAt()).isEqualTo(occurredAt);
    }

    @Test
    void resolveAccess_noStrategyForMethod_throwsIllegalArgumentException() {
        AccessRequest request = new AccessRequest(AccessMethod.TICKET, "T-1", "device-1", null);

        assertThatThrownBy(() -> accessService.resolveAccess(request))
                .isInstanceOf(IllegalArgumentException.class);

        verify(accessLogRepository, never()).save(any());
    }

    @Test
    void resolveAccess_nullAccessMethod_throwsIllegalArgumentException() {
        AccessRequest request = new AccessRequest(null, "AB123CD", "device-1", null);

        assertThatThrownBy(() -> accessService.resolveAccess(request))
                .isInstanceOf(IllegalArgumentException.class);

        verify(accessLogRepository, never()).save(any());
    }
}
