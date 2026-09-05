package com.parko.access.service.strategy;

import com.parko.access.service.client.BalanceClient;
import com.parko.access.service.dto.balance.ChargeRequest;
import com.parko.access.service.dto.request.AccessRequest;
import com.parko.domain.lib.model.AccessEventType;
import com.parko.domain.lib.model.AccessMethod;
import com.parko.domain.lib.model.AccessResult;
import com.parko.domain.lib.model.SessionStatus;
import com.parko.domain.lib.model.TicketStatus;
import com.parko.domain.lib.model.TransactionStatus;
import com.parko.domain.lib.model.TransactionType;
import com.parko.persistence.core.model.entity.ParkingSessionEntity;
import com.parko.persistence.core.model.entity.TicketEntity;
import com.parko.persistence.core.model.entity.TransactionEntity;
import com.parko.persistence.core.model.entity.UserEntity;
import com.parko.persistence.core.model.entity.VehicleEntity;
import com.parko.persistence.core.repository.ParkingSessionRepository;
import com.parko.persistence.core.repository.TicketRepository;
import com.parko.persistence.core.repository.TransactionRepository;
import com.parko.persistence.core.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
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

    private static final BigDecimal ENTRY_FEE = BigDecimal.valueOf(500);

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private ParkingSessionRepository parkingSessionRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private BalanceClient balanceClient;

    private PlateAccessStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new PlateAccessStrategy(vehicleRepository, parkingSessionRepository,
                transactionRepository, ticketRepository, balanceClient, ENTRY_FEE);
    }

    private VehicleEntity vehicleWithUser(UUID vehicleId, UUID userId) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        VehicleEntity vehicle = new VehicleEntity();
        vehicle.setId(vehicleId);
        vehicle.setActive(true);
        vehicle.setUser(user);
        return vehicle;
    }

    private AccessRequest request(String identifier) {
        return new AccessRequest(AccessMethod.PLATE, identifier, "device-1", null);
    }

    @Test
    void supports_returnsPlate() {
        assertThat(strategy.supports()).isEqualTo(AccessMethod.PLATE);
    }

    @Test
    void resolve_activeSessionExistsAndUnpaid_completesSessionAndRetriesCharge() {
        String plate = "AB123CD";
        UUID vehicleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ParkingSessionEntity session = new ParkingSessionEntity();
        session.setId(UUID.randomUUID());
        session.setPlateSnapshot(plate);
        session.setStatus(SessionStatus.ACTIVE);
        session.setVehicle(vehicleWithUser(vehicleId, userId));
        when(parkingSessionRepository.findByPlateSnapshotAndStatus(plate, SessionStatus.ACTIVE))
                .thenReturn(Optional.of(session));
        when(transactionRepository.existsByParkingSession_IdAndTypeAndStatus(
                session.getId(), TransactionType.CHARGE, TransactionStatus.COMPLETED))
                .thenReturn(false);

        AccessDecision decision = strategy.resolve(request(plate));

        assertThat(decision.result()).isEqualTo(AccessResult.AUTHORIZED);
        assertThat(decision.eventType()).isEqualTo(AccessEventType.EXIT);
        assertThat(decision.parkingSessionId()).isEqualTo(session.getId());
        assertThat(session.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(session.getExitAt()).isNotNull();
        verify(parkingSessionRepository).save(session);
        verify(vehicleRepository, never()).findByPlate(any());
        verify(balanceClient).charge(new ChargeRequest(userId, session.getId(), ENTRY_FEE));
    }

    @Test
    void resolve_activeSessionExistsAndAlreadyPaid_completesSessionWithoutRetryingCharge() {
        String plate = "AB123CD";
        ParkingSessionEntity session = new ParkingSessionEntity();
        session.setId(UUID.randomUUID());
        session.setPlateSnapshot(plate);
        session.setStatus(SessionStatus.ACTIVE);
        session.setVehicle(vehicleWithUser(UUID.randomUUID(), UUID.randomUUID()));
        when(parkingSessionRepository.findByPlateSnapshotAndStatus(plate, SessionStatus.ACTIVE))
                .thenReturn(Optional.of(session));
        when(transactionRepository.existsByParkingSession_IdAndTypeAndStatus(
                session.getId(), TransactionType.CHARGE, TransactionStatus.COMPLETED))
                .thenReturn(true);

        AccessDecision decision = strategy.resolve(request(plate));

        assertThat(decision.result()).isEqualTo(AccessResult.AUTHORIZED);
        assertThat(decision.eventType()).isEqualTo(AccessEventType.EXIT);
        verify(balanceClient, never()).charge(any());
    }

    @Test
    void resolve_chargeFails_stillReturnsAuthorized() {
        String plate = "AB123CD";
        VehicleEntity vehicle = vehicleWithUser(UUID.randomUUID(), UUID.randomUUID());
        when(parkingSessionRepository.findByPlateSnapshotAndStatus(plate, SessionStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(vehicleRepository.findByPlate(plate)).thenReturn(Optional.of(vehicle));
        when(parkingSessionRepository.save(any(ParkingSessionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(balanceClient.charge(any())).thenThrow(new RuntimeException("balance-service no disponible"));

        AccessDecision decision = strategy.resolve(request(plate));

        assertThat(decision.result()).isEqualTo(AccessResult.AUTHORIZED);
        assertThat(decision.eventType()).isEqualTo(AccessEventType.ENTRY);
        verify(ticketRepository).save(any(TicketEntity.class));
    }

    @Test
    void resolve_noActiveSessionAndVehicleRegistered_createsSessionChargesEntryFeeAndReturnsEntryAuthorized() {
        String plate = "AB123CD";
        UUID vehicleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        VehicleEntity vehicle = vehicleWithUser(vehicleId, userId);
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
        verify(balanceClient).charge(new ChargeRequest(userId, decision.parkingSessionId(), ENTRY_FEE));

        ArgumentCaptor<TicketEntity> ticketCaptor = ArgumentCaptor.forClass(TicketEntity.class);
        verify(ticketRepository).save(ticketCaptor.capture());
        assertThat(ticketCaptor.getValue().getParkingSession().getId()).isEqualTo(decision.parkingSessionId());
        assertThat(ticketCaptor.getValue().getStatus()).isEqualTo(TicketStatus.PENDING_PAYMENT);
        assertThat(ticketCaptor.getValue().getTicketNumber()).startsWith("TCK-");
    }

    @Test
    void resolve_chargeCompletesAtEntry_marksTicketAsPaid() {
        String plate = "AB123CD";
        UUID vehicleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        VehicleEntity vehicle = vehicleWithUser(vehicleId, userId);
        when(parkingSessionRepository.findByPlateSnapshotAndStatus(plate, SessionStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(vehicleRepository.findByPlate(plate)).thenReturn(Optional.of(vehicle));
        when(parkingSessionRepository.save(any(ParkingSessionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(balanceClient.charge(any())).thenReturn(operationId);
        TransactionEntity completedTransaction = new TransactionEntity();
        completedTransaction.setStatus(TransactionStatus.COMPLETED);
        when(transactionRepository.findById(operationId)).thenReturn(Optional.of(completedTransaction));

        List<TicketEntity> savedTickets = new ArrayList<>();
        when(ticketRepository.save(any(TicketEntity.class))).thenAnswer(invocation -> {
            TicketEntity entity = invocation.getArgument(0);
            savedTickets.add(entity);
            return entity;
        });
        when(ticketRepository.findByParkingSession_Id(any()))
                .thenAnswer(invocation -> savedTickets.isEmpty()
                        ? Optional.empty()
                        : Optional.of(savedTickets.get(savedTickets.size() - 1)));

        strategy.resolve(request(plate));

        TicketEntity finalState = savedTickets.get(savedTickets.size() - 1);
        assertThat(finalState.getStatus()).isEqualTo(TicketStatus.PAID);
        assertThat(finalState.getPaidAt()).isNotNull();
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
