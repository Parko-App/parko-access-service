package com.parko.access.service.event;

import java.math.BigDecimal;
import java.util.UUID;

public record TicketPaymentConfirmedMessage(
        UUID parkingSessionId,
        BigDecimal amount
) {
}
