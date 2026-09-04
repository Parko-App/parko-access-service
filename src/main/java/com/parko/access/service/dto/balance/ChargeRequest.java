package com.parko.access.service.dto.balance;

import java.math.BigDecimal;
import java.util.UUID;

public record ChargeRequest(
        UUID userId,
        UUID parkingSessionId,
        BigDecimal amount
) {
}
