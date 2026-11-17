package com.parko.access.service.service;

import com.parko.access.service.pdf.TicketPdfService;
import com.parko.access.service.strategy.AccessDecision;
import com.parko.domain.lib.model.AccessEventType;
import com.parko.domain.lib.model.AccessResult;
import com.parko.domain.lib.model.SessionStatus;
import com.parko.domain.lib.model.SessionType;
import com.parko.domain.lib.model.TicketStatus;
import com.parko.persistence.core.model.entity.ParkingSessionEntity;
import com.parko.persistence.core.model.entity.TicketEntity;
import com.parko.persistence.core.repository.ParkingSessionRepository;
import com.parko.persistence.core.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VisitorAccessServiceTest {

    private static final String PUBLIC_BASE_URL = "http://localhost:8081";
    private static final String PLATE = "AB123CD";

    @Mock
    private ParkingSessionRepository parkingSessionRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TicketPdfService ticketPdfService;

    private VisitorAccessService service;

    @BeforeEach
    void setUp() {
        service = new VisitorAccessService(parkingSessionRepository, ticketRepository, ticketPdfService,
                PUBLIC_BASE_URL, Duration.ofSeconds(60));
    }

    private ParkingSessionEntity visitorSession(LocalDateTime entryAt) {
        ParkingSessionEntity session = new ParkingSessionEntity();
        session.setId(UUID.randomUUID());
        session.setSessionType(SessionType.VISITOR);
        session.setStatus(SessionStatus.ACTIVE);
        session.setVisitorPlate(PLATE);
        session.setEntryAt(entryAt);
        return session;
    }

    private TicketEntity ticket(ParkingSessionEntity session, TicketStatus status) {
        TicketEntity ticket = new TicketEntity();
        ticket.setId(UUID.randomUUID());
        ticket.setParkingSession(session);
        ticket.setTicketNumber("TCK-ABCD1234");
        ticket.setQrData(PUBLIC_BASE_URL + "/api/v1/access/sessions/" + session.getId() + "/pay");
        ticket.setStatus(status);
        return ticket;
    }

    @Test
    void findActiveSessionByPlate_looksUpNewestActiveVisitorSession() {
        ParkingSessionEntity session = visitorSession(LocalDateTime.now());
        when(parkingSessionRepository.findFirstByVisitorPlateAndStatusOrderByEntryAtDesc(PLATE, SessionStatus.ACTIVE))
                .thenReturn(Optional.of(session));

        assertThat(service.findActiveSessionByPlate(PLATE)).contains(session);
    }

    @Test
    void resolveReturningVisitor_ticketPaid_completesSessionAndAuthorizesExit() {
        ParkingSessionEntity session = visitorSession(LocalDateTime.now().minusHours(2));
        TicketEntity paid = ticket(session, TicketStatus.PAID);
        when(ticketRepository.findByParkingSession_Id(session.getId())).thenReturn(Optional.of(paid));
        when(parkingSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        AccessDecision decision = service.resolveReturningVisitor(session);

        assertThat(decision.result()).isEqualTo(AccessResult.AUTHORIZED);
        assertThat(decision.eventType()).isEqualTo(AccessEventType.EXIT);
        assertThat(decision.replayed()).isFalse();
        assertThat(paid.getStatus()).isEqualTo(TicketStatus.USED);
        assertThat(session.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(session.getExitAt()).isNotNull();
    }

    @Test
    void resolveReturningVisitor_ticketPaidWithinWindow_stillAuthorizesExit() {
        ParkingSessionEntity session = visitorSession(LocalDateTime.now().minusSeconds(5));
        TicketEntity paid = ticket(session, TicketStatus.PAID);
        when(ticketRepository.findByParkingSession_Id(session.getId())).thenReturn(Optional.of(paid));
        when(parkingSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        AccessDecision decision = service.resolveReturningVisitor(session);

        assertThat(decision.result()).isEqualTo(AccessResult.AUTHORIZED);
        assertThat(decision.eventType()).isEqualTo(AccessEventType.EXIT);
        verify(ticketPdfService, never()).generateVisitorTicket(any(), any(), any(), any());
    }

    @Test
    void resolveReturningVisitor_unpaidWithinWindow_resendsSameTicketPdfAsEntry() {
        LocalDateTime entryAt = LocalDateTime.now().minusSeconds(10);
        ParkingSessionEntity session = visitorSession(entryAt);
        TicketEntity pending = ticket(session, TicketStatus.PENDING_PAYMENT);
        byte[] pdf = {1, 2, 3};
        when(ticketRepository.findByParkingSession_Id(session.getId())).thenReturn(Optional.of(pending));
        when(ticketPdfService.generateVisitorTicket("TCK-ABCD1234", PLATE, entryAt, pending.getQrData())).thenReturn(pdf);

        AccessDecision decision = service.resolveReturningVisitor(session);

        assertThat(decision.result()).isEqualTo(AccessResult.AUTHORIZED);
        assertThat(decision.eventType()).isEqualTo(AccessEventType.ENTRY);
        assertThat(decision.parkingSessionId()).isEqualTo(session.getId());
        assertThat(decision.ticketPdf()).isEqualTo(pdf);
        assertThat(decision.replayed()).isTrue();
        verify(parkingSessionRepository, never()).save(any());
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void resolveReturningVisitor_unpaidOutsideWindow_deniesExit() {
        ParkingSessionEntity session = visitorSession(LocalDateTime.now().minusMinutes(5));
        TicketEntity pending = ticket(session, TicketStatus.PENDING_PAYMENT);
        when(ticketRepository.findByParkingSession_Id(session.getId())).thenReturn(Optional.of(pending));

        AccessDecision decision = service.resolveReturningVisitor(session);

        assertThat(decision.result()).isEqualTo(AccessResult.DENIED);
        assertThat(decision.eventType()).isEqualTo(AccessEventType.EXIT);
        assertThat(session.getStatus()).isEqualTo(SessionStatus.ACTIVE);
        verify(parkingSessionRepository, never()).save(any());
        verify(ticketPdfService, never()).generateVisitorTicket(any(), any(), any(), any());
    }

    @Test
    void resolveReturningVisitor_withoutTicket_deniesExit() {
        ParkingSessionEntity session = visitorSession(LocalDateTime.now());
        when(ticketRepository.findByParkingSession_Id(session.getId())).thenReturn(Optional.empty());

        AccessDecision decision = service.resolveReturningVisitor(session);

        assertThat(decision.result()).isEqualTo(AccessResult.DENIED);
        assertThat(decision.eventType()).isEqualTo(AccessEventType.EXIT);
    }
}
