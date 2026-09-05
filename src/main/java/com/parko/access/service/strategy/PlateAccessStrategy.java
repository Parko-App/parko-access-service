package com.parko.access.service.strategy;

import com.parko.access.service.client.BalanceClient;
import com.parko.access.service.converter.ParkingSessionConverter;
import com.parko.access.service.converter.TicketConverter;
import com.parko.access.service.dto.balance.ChargeRequest;
import com.parko.access.service.dto.request.AccessRequest;
import com.parko.domain.lib.model.AccessEventType;
import com.parko.domain.lib.model.AccessMethod;
import com.parko.domain.lib.model.AccessResult;
import com.parko.domain.lib.model.ParkingSession;
import com.parko.domain.lib.model.SessionStatus;
import com.parko.domain.lib.model.Ticket;
import com.parko.domain.lib.model.TicketStatus;
import com.parko.domain.lib.model.TransactionStatus;
import com.parko.domain.lib.model.TransactionType;
import com.parko.persistence.core.model.embedded.ParkingSessionEmbedded;
import com.parko.persistence.core.model.embedded.TicketEmbedded;
import com.parko.persistence.core.model.entity.ParkingSessionEntity;
import com.parko.persistence.core.model.entity.TicketEntity;
import com.parko.persistence.core.model.entity.TransactionEntity;
import com.parko.persistence.core.model.entity.VehicleEntity;
import com.parko.persistence.core.repository.ParkingSessionRepository;
import com.parko.persistence.core.repository.TicketRepository;
import com.parko.persistence.core.repository.TransactionRepository;
import com.parko.persistence.core.repository.VehicleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Component
public class PlateAccessStrategy implements AccessResolutionStrategy {

    private static final Logger log = LoggerFactory.getLogger(PlateAccessStrategy.class);

    private final VehicleRepository vehicleRepository;
    private final ParkingSessionRepository parkingSessionRepository;
    private final TransactionRepository transactionRepository;
    private final TicketRepository ticketRepository;
    private final BalanceClient balanceClient;
    private final BigDecimal entryFee;

    public PlateAccessStrategy(VehicleRepository vehicleRepository,
                                ParkingSessionRepository parkingSessionRepository,
                                TransactionRepository transactionRepository,
                                TicketRepository ticketRepository,
                                BalanceClient balanceClient,
                                @Value("${parking.entry-fee}") BigDecimal entryFee) {
        this.vehicleRepository = vehicleRepository;
        this.parkingSessionRepository = parkingSessionRepository;
        this.transactionRepository = transactionRepository;
        this.ticketRepository = ticketRepository;
        this.balanceClient = balanceClient;
        this.entryFee = entryFee;
    }

    @Override
    public AccessMethod supports() {
        return AccessMethod.PLATE;
    }

    @Override
    public AccessDecision resolve(AccessRequest request) {
        String plate = normalize(request.identifier());
        if (plate.isBlank()) {
            throw new IllegalArgumentException("La patente es requerida para el método PLATE");
        }

        Optional<ParkingSessionEntity> activeSession =
                parkingSessionRepository.findByPlateSnapshotAndStatus(plate, SessionStatus.ACTIVE);
        if (activeSession.isPresent()) {
            return resolveExit(activeSession.get());
        }

        Optional<VehicleEntity> vehicle = vehicleRepository.findByPlate(plate).filter(VehicleEntity::isActive);
        return vehicle.map(vehicleEntity -> resolveEntry(vehicleEntity, plate)).orElseGet(() -> new AccessDecision(AccessResult.DENIED, AccessEventType.ENTRY, null,
                "Patente no registrada: " + plate));

    }

    private AccessDecision resolveExit(ParkingSessionEntity sessionEntity) {
        LocalDateTime exitAt = LocalDateTime.now();
        sessionEntity.setStatus(SessionStatus.COMPLETED);
        sessionEntity.setExitAt(exitAt);
        sessionEntity.setUpdatedAt(exitAt);
        parkingSessionRepository.save(sessionEntity);

        boolean alreadyPaid = transactionRepository.existsByParkingSession_IdAndTypeAndStatus(
                sessionEntity.getId(), TransactionType.CHARGE, TransactionStatus.COMPLETED);
        if (!alreadyPaid) {
            // reintento del cobro que quedó PENDING en la entrada (saldo insuficiente en ese momento)
            chargeEntryFeeAndUpdateTicket(sessionEntity.getVehicle(), sessionEntity.getId());
        }

        return new AccessDecision(AccessResult.AUTHORIZED, AccessEventType.EXIT, sessionEntity.getId(), null);
    }

    private AccessDecision resolveEntry(VehicleEntity vehicle, String plate) {
        LocalDateTime now = LocalDateTime.now();
        ParkingSession session = ParkingSession.forRegisteredUser(UUID.randomUUID(), vehicle.getId(), plate, now);

        ParkingSessionEmbedded embedded = ParkingSessionConverter.toEmbedded(session, now, now);
        ParkingSessionEntity entity = com.parko.persistence.core.converters.ParkingSessionConverter.toEntity(embedded);
        ParkingSessionEntity saved = parkingSessionRepository.save(entity);

        createTicket(saved.getId(), now);
        chargeEntryFeeAndUpdateTicket(vehicle, saved.getId());

        return new AccessDecision(AccessResult.AUTHORIZED, AccessEventType.ENTRY, saved.getId(), null);
    }

    private void createTicket(UUID parkingSessionId, LocalDateTime now) {
        Ticket ticket = new Ticket(parkingSessionId, generateTicketNumber(), generateQrData(parkingSessionId), now);
        TicketEmbedded embedded = TicketConverter.toEmbedded(ticket, now, now);
        TicketEntity entity = com.parko.persistence.core.converters.TicketConverter.toEntity(embedded);
        ticketRepository.save(entity);
    }

    private void chargeEntryFeeAndUpdateTicket(VehicleEntity vehicle, UUID parkingSessionId) {
        Optional<TransactionStatus> resultStatus = chargeEntryFee(vehicle, parkingSessionId);
        if (resultStatus.filter(status -> status == TransactionStatus.COMPLETED).isPresent()) {
            markTicketAsPaid(parkingSessionId);
        }
    }

    private Optional<TransactionStatus> chargeEntryFee(VehicleEntity vehicle, UUID parkingSessionId) {
        try {
            UUID operationId = balanceClient.charge(new ChargeRequest(vehicle.getUser().getId(), parkingSessionId, entryFee));
            return transactionRepository.findById(operationId).map(TransactionEntity::getStatus);
        } catch (RuntimeException e) {
            // el cobro es best-effort: la barrera abre igual (ADR 0001, saldo irrelevante para abrir), la transacción queda sin registrar
            log.error("Fallo al cobrar el estacionamiento para parkingSessionId={}: {}", parkingSessionId, e.getMessage(), e);
            return Optional.empty();
        }
    }

    private void markTicketAsPaid(UUID parkingSessionId) {
        ticketRepository.findByParkingSession_Id(parkingSessionId)
                .filter(ticket -> ticket.getStatus() == TicketStatus.PENDING_PAYMENT)
                .ifPresent(ticket -> {
                    LocalDateTime now = LocalDateTime.now();
                    ticket.setStatus(TicketStatus.PAID);
                    ticket.setPaidAt(now);
                    ticket.setUpdatedAt(now);
                    ticketRepository.save(ticket);
                });
    }

    private String generateTicketNumber() {
        return "TCK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String generateQrData(UUID parkingSessionId) {
        return parkingSessionId.toString();
    }

    private String normalize(String identifier) {
        return identifier == null ? "" : identifier.replaceAll("\\s+", "").toUpperCase();
    }
}
