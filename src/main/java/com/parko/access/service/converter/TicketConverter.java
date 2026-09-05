package com.parko.access.service.converter;

import com.parko.domain.lib.model.Ticket;
import com.parko.persistence.core.model.embedded.TicketEmbedded;

import java.time.LocalDateTime;

public final class TicketConverter {

    private TicketConverter() {
    }

    public static TicketEmbedded toEmbedded(Ticket ticket, LocalDateTime createdAt, LocalDateTime updatedAt) {
        return new TicketEmbedded(
                ticket.getParkingSessionId(),
                ticket.getTicketNumber(),
                ticket.getQrData(),
                ticket.getStatus(),
                ticket.getIssuedAt(),
                ticket.getPaidAt(),
                createdAt,
                updatedAt
        );
    }
}
