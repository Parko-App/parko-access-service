package com.parko.access.service.strategy;

import com.parko.access.service.converter.ParkingSessionConverter;
import com.parko.access.service.converter.TicketConverter;
import com.parko.access.service.dto.request.AccessRequest;
import com.parko.access.service.pdf.TicketPdfService;
import com.parko.domain.lib.model.AccessEventType;
import com.parko.domain.lib.model.AccessMethod;
import com.parko.domain.lib.model.AccessResult;
import com.parko.domain.lib.model.ParkingSession;
import com.parko.domain.lib.model.SessionStatus;
import com.parko.domain.lib.model.Ticket;
import com.parko.domain.lib.model.TicketStatus;
import com.parko.persistence.core.model.embedded.ParkingSessionEmbedded;
import com.parko.persistence.core.model.embedded.TicketEmbedded;
import com.parko.persistence.core.model.entity.ParkingSessionEntity;
import com.parko.persistence.core.model.entity.TicketEntity;
import com.parko.persistence.core.repository.ParkingSessionRepository;
import com.parko.persistence.core.repository.TicketRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Component
public class TotemAccessStrategy implements AccessResolutionStrategy {

    private final ParkingSessionRepository parkingSessionRepository;
    private final TicketRepository ticketRepository;
    private final TicketPdfService ticketPdfService;

    public TotemAccessStrategy(ParkingSessionRepository parkingSessionRepository, TicketRepository ticketRepository,
                                TicketPdfService ticketPdfService) {
        this.parkingSessionRepository = parkingSessionRepository;
        this.ticketRepository = ticketRepository;
        this.ticketPdfService = ticketPdfService;
    }

    @Override
    public AccessMethod supports() {
        return AccessMethod.TICKET;
    }

    @Override
    public AccessDecision resolve(AccessRequest request) {
        String identifier = request.identifier();
        if (identifier == null || identifier.isBlank()) {
            return resolveEntry(normalizePlate(request.visitorPlate()));
        }
        return resolveExit(identifier.trim());
    }

    private AccessDecision resolveEntry(String visitorPlate) {
        LocalDateTime now = LocalDateTime.now();
        ParkingSession session = ParkingSession.forVisitor(UUID.randomUUID(), now, visitorPlate);

        ParkingSessionEmbedded embedded = ParkingSessionConverter.toEmbedded(session, now, now);
        ParkingSessionEntity entity = com.parko.persistence.core.converters.ParkingSessionConverter.toEntity(embedded);
        ParkingSessionEntity saved = parkingSessionRepository.save(entity);

        String ticketNumber = createTicket(saved.getId(), now);
        byte[] ticketPdf = ticketPdfService.generateVisitorTicket(ticketNumber, visitorPlate, now);

        return new AccessDecision(AccessResult.AUTHORIZED, AccessEventType.ENTRY, saved.getId(), null, ticketPdf);
    }

    private String createTicket(UUID parkingSessionId, LocalDateTime now) {
        String ticketNumber = generateTicketNumber();
        Ticket ticket = new Ticket(parkingSessionId, ticketNumber, generateQrData(parkingSessionId), now);
        TicketEmbedded embedded = TicketConverter.toEmbedded(ticket, now, now);
        TicketEntity entity = com.parko.persistence.core.converters.TicketConverter.toEntity(embedded);
        ticketRepository.save(entity);
        return ticketNumber;
    }

    private AccessDecision resolveExit(String identifier) {
        UUID parkingSessionId;
        try {
            parkingSessionId = UUID.fromString(identifier);
        } catch (IllegalArgumentException e) {
            return new AccessDecision(AccessResult.DENIED, AccessEventType.EXIT, null, "Ticket inválido: " + identifier, null);
        }

        Optional<TicketEntity> ticketOpt = ticketRepository.findByParkingSession_Id(parkingSessionId);
        if (ticketOpt.isEmpty() || ticketOpt.get().getStatus() != TicketStatus.PAID) {
            return new AccessDecision(AccessResult.DENIED, AccessEventType.EXIT, parkingSessionId, "Ticket no encontrado o no pagado", null);
        }

        Optional<ParkingSessionEntity> sessionOpt = parkingSessionRepository.findById(parkingSessionId)
                .filter(session -> session.getStatus() == SessionStatus.ACTIVE);
        if (sessionOpt.isEmpty()) {
            return new AccessDecision(AccessResult.DENIED, AccessEventType.EXIT, parkingSessionId, "Sesión no activa", null);
        }

        LocalDateTime exitAt = LocalDateTime.now();

        TicketEntity ticket = ticketOpt.get();
        ticket.setStatus(TicketStatus.USED);
        ticket.setUpdatedAt(exitAt);
        ticketRepository.save(ticket);

        ParkingSessionEntity session = sessionOpt.get();
        session.setStatus(SessionStatus.COMPLETED);
        session.setExitAt(exitAt);
        session.setUpdatedAt(exitAt);
        parkingSessionRepository.save(session);

        return new AccessDecision(AccessResult.AUTHORIZED, AccessEventType.EXIT, session.getId(), null, null);
    }

    private String generateTicketNumber() {
        return "TCK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String generateQrData(UUID parkingSessionId) {
        return parkingSessionId.toString();
    }

    private String normalizePlate(String plate) {
        if (plate == null || plate.isBlank()) {
            return null;
        }
        return plate.replaceAll("\\s+", "").toUpperCase();
    }
}
