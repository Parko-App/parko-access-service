package com.parko.access.service.dto.externalgateway;

import java.math.BigDecimal;
import java.util.UUID;

public record TicketPaymentRequest(
        UUID parkingSessionId,
        BigDecimal amount
) {
}
