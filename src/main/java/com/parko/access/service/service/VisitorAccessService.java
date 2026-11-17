package com.parko.access.service.service;

import com.parko.access.service.converter.ParkingSessionConverter;
import com.parko.access.service.converter.TicketConverter;
import com.parko.access.service.pdf.TicketPdfService;
import com.parko.access.service.strategy.AccessDecision;
import com.parko.domain.lib.model.AccessEventType;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class VisitorAccessService {

    private final ParkingSessionRepository parkingSessionRepository;
    private final TicketRepository ticketRepository;
    private final TicketPdfService ticketPdfService;
    private final String publicBaseUrl;
    private final Duration entryIdempotencyWindow;

    public VisitorAccessService(ParkingSessionRepository parkingSessionRepository,
                                TicketRepository ticketRepository,
                                TicketPdfService ticketPdfService,
                                @Value("${access-service.public-base-url}") String publicBaseUrl,
                                @Value("${access-service.entry-idempotency-window:PT60S}") Duration entryIdempotencyWindow) {
        this.parkingSessionRepository = parkingSessionRepository;
        this.ticketRepository = ticketRepository;
        this.ticketPdfService = ticketPdfService;
        this.publicBaseUrl = publicBaseUrl;
        this.entryIdempotencyWindow = entryIdempotencyWindow;
    }

    public AccessDecision enter(String visitorPlate) {
        LocalDateTime now = LocalDateTime.now();
        ParkingSession session = ParkingSession.forVisitor(UUID.randomUUID(), now, visitorPlate);

        ParkingSessionEmbedded embedded = ParkingSessionConverter.toEmbedded(session, now, now);
        ParkingSessionEntity entity = com.parko.persistence.core.converters.ParkingSessionConverter.toEntity(embedded);
        ParkingSessionEntity saved = parkingSessionRepository.save(entity);

        String paymentUrl = buildPaymentUrl(saved.getId());
        String ticketNumber = createTicket(saved.getId(), now, paymentUrl);
        byte[] ticketPdf = ticketPdfService.generateVisitorTicket(ticketNumber, visitorPlate, now, paymentUrl);

        return new AccessDecision(AccessResult.AUTHORIZED, AccessEventType.ENTRY, saved.getId(), null, ticketPdf);
    }

    public Optional<ParkingSessionEntity> findActiveSessionByPlate(String visitorPlate) {
        return parkingSessionRepository.findFirstByVisitorPlateAndStatusOrderByEntryAtDesc(visitorPlate, SessionStatus.ACTIVE);
    }

    public AccessDecision resolveReturningVisitor(ParkingSessionEntity session) {
        Optional<TicketEntity> ticketOpt = ticketRepository.findByParkingSession_Id(session.getId());
        if (ticketOpt.isPresent() && isRecentUnpaidEntry(session, ticketOpt.get())) {
            return resendEntry(session, ticketOpt.get());
        }
        return exit(session.getId());
    }

    public AccessDecision exit(UUID parkingSessionId) {
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

    private boolean isRecentUnpaidEntry(ParkingSessionEntity session, TicketEntity ticket) {
        if (ticket.getStatus() != TicketStatus.PENDING_PAYMENT || session.getEntryAt() == null) {
            return false;
        }
        LocalDateTime limit = session.getEntryAt().plus(entryIdempotencyWindow);
        return LocalDateTime.now().isBefore(limit);
    }

    private AccessDecision resendEntry(ParkingSessionEntity session, TicketEntity ticket) {
        byte[] ticketPdf = ticketPdfService.generateVisitorTicket(
                ticket.getTicketNumber(), session.getVisitorPlate(), session.getEntryAt(), ticket.getQrData());
        return new AccessDecision(AccessResult.AUTHORIZED, AccessEventType.ENTRY, session.getId(), null, ticketPdf, true);
    }

    private String buildPaymentUrl(UUID parkingSessionId) {
        return publicBaseUrl + "/api/v1/access/sessions/" + parkingSessionId + "/pay";
    }

    private String createTicket(UUID parkingSessionId, LocalDateTime now, String paymentUrl) {
        String ticketNumber = generateTicketNumber();
        Ticket ticket = new Ticket(parkingSessionId, ticketNumber, paymentUrl, now);
        TicketEmbedded embedded = TicketConverter.toEmbedded(ticket, now, now);
        TicketEntity entity = com.parko.persistence.core.converters.TicketConverter.toEntity(embedded);
        ticketRepository.save(entity);
        return ticketNumber;
    }

    private String generateTicketNumber() {
        return "TCK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
