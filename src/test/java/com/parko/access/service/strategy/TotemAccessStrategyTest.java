package com.parko.access.service.strategy;

import com.parko.access.service.dto.request.AccessRequest;
import com.parko.access.service.pdf.TicketPdfService;
import com.parko.domain.lib.model.AccessEventType;
import com.parko.domain.lib.model.AccessMethod;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TotemAccessStrategyTest {

    @Mock
    private ParkingSessionRepository parkingSessionRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TicketPdfService ticketPdfService;

    private TotemAccessStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new TotemAccessStrategy(parkingSessionRepository, ticketRepository, ticketPdfService);
    }

    private AccessRequest request(String identifier) {
        return request(identifier, null);
    }

    private AccessRequest request(String identifier, String visitorPlate) {
        return new AccessRequest(AccessMethod.TICKET, identifier, "totem-1", null, visitorPlate);
    }

    private ParkingSessionEntity activeSession(UUID id) {
        ParkingSessionEntity session = new ParkingSessionEntity();
        session.setId(id);
        session.setStatus(SessionStatus.ACTIVE);
        session.setSessionType(SessionType.VISITOR);
        return session;
    }

    private TicketEntity ticket(TicketStatus status) {
        TicketEntity ticket = new TicketEntity();
        ticket.setId(UUID.randomUUID());
        ticket.setStatus(status);
        return ticket;
    }

    @Test
    void supports_returnsTicket() {
        assertThat(strategy.supports()).isEqualTo(AccessMethod.TICKET);
    }

    @Test
    void resolve_blankIdentifier_createsVisitorSessionAndTicket() {
        when(parkingSessionRepository.save(any())).thenAnswer(invocation -> {
            ParkingSessionEntity entity = invocation.getArgument(0);
            entity.setId(UUID.randomUUID());
            return entity;
        });

        AccessDecision decision = strategy.resolve(request(null));

        assertThat(decision.result()).isEqualTo(AccessResult.AUTHORIZED);
        assertThat(decision.eventType()).isEqualTo(AccessEventType.ENTRY);
        assertThat(decision.parkingSessionId()).isNotNull();

        ArgumentCaptor<ParkingSessionEntity> sessionCaptor = ArgumentCaptor.forClass(ParkingSessionEntity.class);
        verify(parkingSessionRepository).save(sessionCaptor.capture());
        assertThat(sessionCaptor.getValue().getSessionType()).isEqualTo(SessionType.VISITOR);
        assertThat(sessionCaptor.getValue().getVehicle()).isNull();

        ArgumentCaptor<TicketEntity> ticketCaptor = ArgumentCaptor.forClass(TicketEntity.class);
        verify(ticketRepository).save(ticketCaptor.capture());
        assertThat(ticketCaptor.getValue().getStatus()).isEqualTo(TicketStatus.PENDING_PAYMENT);
        assertThat(ticketCaptor.getValue().getParkingSession().getId()).isEqualTo(decision.parkingSessionId());
    }

    @Test
    void resolve_blankIdentifierWithVisitorPlate_setsNormalizedVisitorPlateOnSession() {
        when(parkingSessionRepository.save(any())).thenAnswer(invocation -> {
            ParkingSessionEntity entity = invocation.getArgument(0);
            entity.setId(UUID.randomUUID());
            return entity;
        });

        strategy.resolve(request(null, " ab 123 cd "));

        ArgumentCaptor<ParkingSessionEntity> sessionCaptor = ArgumentCaptor.forClass(ParkingSessionEntity.class);
        verify(parkingSessionRepository).save(sessionCaptor.capture());
        assertThat(sessionCaptor.getValue().getVisitorPlate()).isEqualTo("AB123CD");
        assertThat(sessionCaptor.getValue().getPlateSnapshot()).isNull();
    }

    @Test
    void resolve_blankIdentifier_returnsGeneratedTicketPdf() {
        byte[] pdfBytes = {1, 2, 3};
        when(parkingSessionRepository.save(any())).thenAnswer(invocation -> {
            ParkingSessionEntity entity = invocation.getArgument(0);
            entity.setId(UUID.randomUUID());
            return entity;
        });
        when(ticketPdfService.generateVisitorTicket(any(), any(), any())).thenReturn(pdfBytes);

        AccessDecision decision = strategy.resolve(request(null, "AB123CD"));

        assertThat(decision.ticketPdf()).isEqualTo(pdfBytes);
        verify(ticketPdfService).generateVisitorTicket(any(), eq("AB123CD"), any());
    }

    @Test
    void resolve_invalidIdentifier_returnsDenied() {
        AccessDecision decision = strategy.resolve(request("no-es-un-uuid"));

        assertThat(decision.result()).isEqualTo(AccessResult.DENIED);
        assertThat(decision.eventType()).isEqualTo(AccessEventType.EXIT);
        verify(ticketRepository, never()).findByParkingSession_Id(any());
    }

    @Test
    void resolve_ticketNotFound_returnsDenied() {
        UUID sessionId = UUID.randomUUID();
        when(ticketRepository.findByParkingSession_Id(sessionId)).thenReturn(Optional.empty());

        AccessDecision decision = strategy.resolve(request(sessionId.toString()));

        assertThat(decision.result()).isEqualTo(AccessResult.DENIED);
        assertThat(decision.eventType()).isEqualTo(AccessEventType.EXIT);
        verify(parkingSessionRepository, never()).findById(any());
    }

    @Test
    void resolve_ticketNotPaid_returnsDenied() {
        UUID sessionId = UUID.randomUUID();
        when(ticketRepository.findByParkingSession_Id(sessionId)).thenReturn(Optional.of(ticket(TicketStatus.PENDING_PAYMENT)));

        AccessDecision decision = strategy.resolve(request(sessionId.toString()));

        assertThat(decision.result()).isEqualTo(AccessResult.DENIED);
        assertThat(decision.eventType()).isEqualTo(AccessEventType.EXIT);
        verify(parkingSessionRepository, never()).save(any());
    }

    @Test
    void resolve_ticketPaidButSessionNotActive_returnsDenied() {
        UUID sessionId = UUID.randomUUID();
        when(ticketRepository.findByParkingSession_Id(sessionId)).thenReturn(Optional.of(ticket(TicketStatus.PAID)));
        when(parkingSessionRepository.findById(sessionId)).thenReturn(Optional.empty());

        AccessDecision decision = strategy.resolve(request(sessionId.toString()));

        assertThat(decision.result()).isEqualTo(AccessResult.DENIED);
        assertThat(decision.eventType()).isEqualTo(AccessEventType.EXIT);
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void resolve_ticketPaidAndSessionActive_completesSessionAndMarksTicketUsed() {
        UUID sessionId = UUID.randomUUID();
        TicketEntity paidTicket = ticket(TicketStatus.PAID);
        ParkingSessionEntity session = activeSession(sessionId);
        when(ticketRepository.findByParkingSession_Id(sessionId)).thenReturn(Optional.of(paidTicket));
        when(parkingSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        AccessDecision decision = strategy.resolve(request(sessionId.toString()));

        assertThat(decision.result()).isEqualTo(AccessResult.AUTHORIZED);
        assertThat(decision.eventType()).isEqualTo(AccessEventType.EXIT);
        assertThat(decision.parkingSessionId()).isEqualTo(sessionId);
        assertThat(paidTicket.getStatus()).isEqualTo(TicketStatus.USED);
        assertThat(session.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(session.getExitAt()).isNotNull();
        verify(ticketRepository).save(paidTicket);
        verify(parkingSessionRepository).save(session);
    }
}
