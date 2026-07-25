package com.parko.access.service.strategy;

import com.parko.access.service.dto.request.AccessRequest;
import com.parko.domain.lib.model.AccessEventType;
import com.parko.domain.lib.model.AccessMethod;
import com.parko.domain.lib.model.AccessResult;
import com.parko.domain.lib.model.SessionStatus;
import com.parko.persistence.core.model.entity.ParkingSessionEntity;
import com.parko.persistence.core.model.entity.VehicleEntity;
import com.parko.persistence.core.repository.ParkingSessionRepository;
import com.parko.persistence.core.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlateAccessStrategyTest {

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private ParkingSessionRepository parkingSessionRepository;

    private PlateAccessStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new PlateAccessStrategy(vehicleRepository, parkingSessionRepository);
    }

    private AccessRequest request(String identifier) {
        return new AccessRequest(AccessMethod.PLATE, identifier, "device-1", null);
    }

    @Test
    void supports_returnsPlate() {
        assertThat(strategy.supports()).isEqualTo(AccessMethod.PLATE);
    }

    @Test
    void resolve_activeSessionExists_completesSessionAndReturnsExitAuthorized() {
        String plate = "AB123CD";
        ParkingSessionEntity session = new ParkingSessionEntity();
        session.setId(UUID.randomUUID());
        session.setPlateSnapshot(plate);
        session.setStatus(SessionStatus.ACTIVE);
        when(parkingSessionRepository.findByPlateSnapshotAndStatus(plate, SessionStatus.ACTIVE))
                .thenReturn(Optional.of(session));

        AccessDecision decision = strategy.resolve(request(plate));

        assertThat(decision.result()).isEqualTo(AccessResult.AUTHORIZED);
        assertThat(decision.eventType()).isEqualTo(AccessEventType.EXIT);
        assertThat(decision.parkingSessionId()).isEqualTo(session.getId());
        assertThat(session.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(session.getExitAt()).isNotNull();
        verify(parkingSessionRepository).save(session);
        verify(vehicleRepository, never()).findByPlate(any());
    }

    @Test
    void resolve_noActiveSessionAndVehicleRegistered_createsSessionAndReturnsEntryAuthorized() {
        String plate = "AB123CD";
        UUID vehicleId = UUID.randomUUID();
        VehicleEntity vehicle = new VehicleEntity();
        vehicle.setId(vehicleId);
        vehicle.setActive(true);
        when(parkingSessionRepository.findByPlateSnapshotAndStatus(plate, SessionStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(vehicleRepository.findByPlate(plate)).thenReturn(Optional.of(vehicle));
        when(parkingSessionRepository.save(any(ParkingSessionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AccessDecision decision = strategy.resolve(request(plate));

        assertThat(decision.result()).isEqualTo(AccessResult.AUTHORIZED);
        assertThat(decision.eventType()).isEqualTo(AccessEventType.ENTRY);
        assertThat(decision.parkingSessionId()).isNotNull();

        ArgumentCaptor<ParkingSessionEntity> captor = ArgumentCaptor.forClass(ParkingSessionEntity.class);
        verify(parkingSessionRepository).save(captor.capture());
        assertThat(captor.getValue().getPlateSnapshot()).isEqualTo(plate);
        assertThat(captor.getValue().getVehicle().getId()).isEqualTo(vehicleId);
        assertThat(captor.getValue().getStatus()).isEqualTo(SessionStatus.ACTIVE);
    }

    @Test
    void resolve_plateNotRegistered_returnsDeniedWithoutCreatingSession() {
        String plate = "ZZ999ZZ";
        when(parkingSessionRepository.findByPlateSnapshotAndStatus(plate, SessionStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(vehicleRepository.findByPlate(plate)).thenReturn(Optional.empty());

        AccessDecision decision = strategy.resolve(request(plate));

        assertThat(decision.result()).isEqualTo(AccessResult.DENIED);
        assertThat(decision.eventType()).isEqualTo(AccessEventType.ENTRY);
        assertThat(decision.parkingSessionId()).isNull();
        verify(parkingSessionRepository, never()).save(any());
    }

    @Test
    void resolve_vehicleInactive_returnsDenied() {
        String plate = "AB123CD";
        VehicleEntity vehicle = new VehicleEntity();
        vehicle.setId(UUID.randomUUID());
        vehicle.setActive(false);
        when(parkingSessionRepository.findByPlateSnapshotAndStatus(plate, SessionStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(vehicleRepository.findByPlate(plate)).thenReturn(Optional.of(vehicle));

        AccessDecision decision = strategy.resolve(request(plate));

        assertThat(decision.result()).isEqualTo(AccessResult.DENIED);
        verify(parkingSessionRepository, never()).save(any());
    }

    @Test
    void resolve_blankIdentifier_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> strategy.resolve(request("   ")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resolve_nullIdentifier_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> strategy.resolve(request(null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resolve_normalizesIdentifierBeforeLookup() {
        when(parkingSessionRepository.findByPlateSnapshotAndStatus("AB123CD", SessionStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(vehicleRepository.findByPlate("AB123CD")).thenReturn(Optional.empty());

        strategy.resolve(request(" ab 123 cd "));

        verify(parkingSessionRepository).findByPlateSnapshotAndStatus("AB123CD", SessionStatus.ACTIVE);
        verify(vehicleRepository).findByPlate("AB123CD");
    }
}
