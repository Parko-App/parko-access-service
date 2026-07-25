package com.parko.access.service.dto.response;

import com.parko.domain.lib.model.AccessEventType;
import com.parko.domain.lib.model.AccessResult;

public record AccessResponse(
        AccessResult result,
        AccessEventType eventType,
        String message
) {
}
