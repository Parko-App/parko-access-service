package com.parko.access.service.service;

import com.parko.access.service.client.ExternalGatewayClient;
import com.parko.access.service.dto.externalgateway.TicketPaymentPreferenceResponse;
import com.parko.access.service.dto.externalgateway.TicketPaymentRequest;
import com.parko.domain.lib.model.TicketStatus;
import com.parko.persistence.core.model.entity.TicketEntity;
import com.parko.persistence.core.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    private static final BigDecimal VISITOR_ENTRY_FEE = BigDecimal.valueOf(3000);

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private ExternalGatewayClient externalGatewayClient;

    private TicketService ticketService;

    @BeforeEach
    void setUp() {
        ticketService = new TicketService(ticketRepository, externalGatewayClient, VISITOR_ENTRY_FEE);
    }

    private TicketEntity ticket(TicketStatus status) {
        TicketEntity ticket = new TicketEntity();
        ticket.setId(UUID.randomUUID());
        ticket.setStatus(status);
        return ticket;
    }

    @Test
    void resolvePayment_sessionNotFound_returnsNotFound() {
        UUID parkingSessionId = UUID.randomUUID();
        when(ticketRepository.findByParkingSession_Id(parkingSessionId)).thenReturn(Optional.empty());

        TicketPaymentResolution resolution = ticketService.resolvePayment(parkingSessionId);

        assertThat(resolution.outcome()).isEqualTo(TicketPaymentResolution.Outcome.NOT_FOUND);
        verify(externalGatewayClient, never()).createTicketPayment(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void resolvePayment_ticketAlreadyPaid_returnsAlreadyPaid() {
        UUID parkingSessionId = UUID.randomUUID();
        when(ticketRepository.findByParkingSession_Id(parkingSessionId)).thenReturn(Optional.of(ticket(TicketStatus.PAID)));

        TicketPaymentResolution resolution = ticketService.resolvePayment(parkingSessionId);

        assertThat(resolution.outcome()).isEqualTo(TicketPaymentResolution.Outcome.ALREADY_PAID);
        verify(externalGatewayClient, never()).createTicketPayment(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void resolvePayment_ticketUsed_returnsUnavailable() {
        UUID parkingSessionId = UUID.randomUUID();
        when(ticketRepository.findByParkingSession_Id(parkingSessionId)).thenReturn(Optional.of(ticket(TicketStatus.USED)));

        TicketPaymentResolution resolution = ticketService.resolvePayment(parkingSessionId);

        assertThat(resolution.outcome()).isEqualTo(TicketPaymentResolution.Outcome.UNAVAILABLE);
        assertThat(resolution.message()).contains("USED");
    }

    @Test
    void resolvePayment_ticketCancelled_returnsUnavailable() {
        UUID parkingSessionId = UUID.randomUUID();
        when(ticketRepository.findByParkingSession_Id(parkingSessionId)).thenReturn(Optional.of(ticket(TicketStatus.CANCELLED)));

        TicketPaymentResolution resolution = ticketService.resolvePayment(parkingSessionId);

        assertThat(resolution.outcome()).isEqualTo(TicketPaymentResolution.Outcome.UNAVAILABLE);
    }

    @Test
    void resolvePayment_ticketPending_redirectsToCheckout() {
        UUID parkingSessionId = UUID.randomUUID();
        when(ticketRepository.findByParkingSession_Id(parkingSessionId))
                .thenReturn(Optional.of(ticket(TicketStatus.PENDING_PAYMENT)));
        when(externalGatewayClient.createTicketPayment(new TicketPaymentRequest(parkingSessionId, VISITOR_ENTRY_FEE)))
                .thenReturn(new TicketPaymentPreferenceResponse("pref-1", "https://mp.com/checkout", "https://mp.com/sandbox"));

        TicketPaymentResolution resolution = ticketService.resolvePayment(parkingSessionId);

        assertThat(resolution.outcome()).isEqualTo(TicketPaymentResolution.Outcome.REDIRECT);
        assertThat(resolution.checkoutUrl()).isEqualTo("https://mp.com/checkout");
    }
}
