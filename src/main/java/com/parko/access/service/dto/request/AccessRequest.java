package com.parko.access.service.dto.request;

import com.parko.domain.lib.model.AccessMethod;

import java.time.LocalDateTime;

public record AccessRequest(
        AccessMethod accessMethod,
        String identifier,
        String deviceId,
        LocalDateTime occurredAt
) {
}
