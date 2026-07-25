package com.parko.access.service.strategy;

import com.parko.domain.lib.model.AccessEventType;
import com.parko.domain.lib.model.AccessResult;

import java.util.UUID;

public record AccessDecision(
        AccessResult result,
        AccessEventType eventType,
        UUID parkingSessionId,
        String message
) {
}
