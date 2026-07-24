package com.parko.access.service.converter;

import com.parko.domain.lib.model.ParkingSession;
import com.parko.persistence.core.model.embedded.ParkingSessionEmbedded;

import java.time.LocalDateTime;

public final class ParkingSessionConverter {

    private ParkingSessionConverter() {
    }

    public static ParkingSessionEmbedded toEmbedded(ParkingSession session, LocalDateTime createdAt, LocalDateTime updatedAt) {
        return new ParkingSessionEmbedded(
                session.getId(),
                session.getVehicleId(),
                session.getPlateSnapshot(),
                session.getSessionType(),
                session.getStatus(),
                session.getEntryAt(),
                session.getExitAt(),
                createdAt,
                updatedAt
        );
    }
}
