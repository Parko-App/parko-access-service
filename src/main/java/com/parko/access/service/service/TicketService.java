package com.parko.access.service.service;

import com.parko.access.service.client.ExternalGatewayClient;
import com.parko.access.service.dto.externalgateway.TicketPaymentPreferenceResponse;
import com.parko.access.service.dto.externalgateway.TicketPaymentRequest;
import com.parko.persistence.core.model.entity.TicketEntity;
import com.parko.persistence.core.repository.TicketRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final ExternalGatewayClient externalGatewayClient;
    private final BigDecimal visitorEntryFee;

    public TicketService(TicketRepository ticketRepository,
                          ExternalGatewayClient externalGatewayClient,
                          @Value("${parking.visitor-entry-fee}") BigDecimal visitorEntryFee) {
        this.ticketRepository = ticketRepository;
        this.externalGatewayClient = externalGatewayClient;
        this.visitorEntryFee = visitorEntryFee;
    }

    public TicketPaymentResolution resolvePayment(UUID parkingSessionId) {
        return ticketRepository.findByParkingSession_Id(parkingSessionId)
                .map(ticket -> resolve(parkingSessionId, ticket))
                .orElseGet(TicketPaymentResolution::notFound);
    }

    private TicketPaymentResolution resolve(UUID parkingSessionId, TicketEntity ticket) {
        return switch (ticket.getStatus()) {
            case PAID -> TicketPaymentResolution.alreadyPaid();
            case USED, CANCELLED -> TicketPaymentResolution.unavailable(ticket.getStatus());
            case PENDING_PAYMENT -> TicketPaymentResolution.redirect(createCheckout(parkingSessionId));
        };
    }

    private String createCheckout(UUID parkingSessionId) {
        TicketPaymentPreferenceResponse preference = externalGatewayClient.createTicketPayment(
                new TicketPaymentRequest(parkingSessionId, visitorEntryFee));
        return preference.initPoint();
    }
}
