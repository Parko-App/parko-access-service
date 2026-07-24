package com.parko.access.service.converter;

import com.parko.domain.lib.model.AccessLog;
import com.parko.persistence.core.model.embedded.AccessLogEmbedded;

import java.time.LocalDateTime;

public final class AccessLogConverter {

    private AccessLogConverter() {
    }

    public static AccessLogEmbedded toEmbedded(AccessLog accessLog, LocalDateTime createdAt, LocalDateTime updatedAt) {
        return new AccessLogEmbedded(
                accessLog.getId(),
                accessLog.getParkingSessionId(),
                accessLog.getEventType(),
                accessLog.getAccessMethod(),
                accessLog.getResult(),
                accessLog.getRawPayload(),
                accessLog.getOccurredAt(),
                createdAt,
                updatedAt
        );
    }

    public static AccessLog toDomain(AccessLogEmbedded embedded) {
        return new AccessLog(
                embedded.id(),
                embedded.parkingSessionId(),
                embedded.eventType(),
                embedded.accessMethod(),
                embedded.result(),
                embedded.rawPayload(),
                embedded.occurredAt()
        );
    }
}
