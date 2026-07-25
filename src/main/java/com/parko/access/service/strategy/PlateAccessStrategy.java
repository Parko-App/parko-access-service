package com.parko.access.service.strategy;

import com.parko.access.service.converter.ParkingSessionConverter;
import com.parko.access.service.dto.request.AccessRequest;
import com.parko.domain.lib.model.AccessEventType;
import com.parko.domain.lib.model.AccessMethod;
import com.parko.domain.lib.model.AccessResult;
import com.parko.domain.lib.model.ParkingSession;
import com.parko.domain.lib.model.SessionStatus;
import com.parko.persistence.core.model.embedded.ParkingSessionEmbedded;
import com.parko.persistence.core.model.entity.ParkingSessionEntity;
import com.parko.persistence.core.model.entity.VehicleEntity;
import com.parko.persistence.core.repository.ParkingSessionRepository;
import com.parko.persistence.core.repository.VehicleRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Component
public class PlateAccessStrategy implements AccessResolutionStrategy {

    private final VehicleRepository vehicleRepository;
    private final ParkingSessionRepository parkingSessionRepository;

    public PlateAccessStrategy(VehicleRepository vehicleRepository, ParkingSessionRepository parkingSessionRepository) {
        this.vehicleRepository = vehicleRepository;
        this.parkingSessionRepository = parkingSessionRepository;
    }

    @Override
    public AccessMethod supports() {
        return AccessMethod.PLATE;
    }

    @Override
    public AccessDecision resolve(AccessRequest request) {
        String plate = normalize(request.identifier());
        if (plate.isBlank()) {
            throw new IllegalArgumentException("La patente es requerida para el método PLATE");
        }

        Optional<ParkingSessionEntity> activeSession =
                parkingSessionRepository.findByPlateSnapshotAndStatus(plate, SessionStatus.ACTIVE);
        if (activeSession.isPresent()) {
            return resolveExit(activeSession.get());
        }

        Optional<VehicleEntity> vehicle = vehicleRepository.findByPlate(plate).filter(VehicleEntity::isActive);
        return vehicle.map(vehicleEntity -> resolveEntry(vehicleEntity, plate)).orElseGet(() -> new AccessDecision(AccessResult.DENIED, AccessEventType.ENTRY, null,
                "Patente no registrada: " + plate));

    }

    private AccessDecision resolveExit(ParkingSessionEntity sessionEntity) {
        LocalDateTime exitAt = LocalDateTime.now();
        sessionEntity.setStatus(SessionStatus.COMPLETED);
        sessionEntity.setExitAt(exitAt);
        sessionEntity.setUpdatedAt(exitAt);
        parkingSessionRepository.save(sessionEntity);

        // TODO: cobrar la estadía contra el artefacto de balance (débito por estadia) una vez que exista
        return new AccessDecision(AccessResult.AUTHORIZED, AccessEventType.EXIT, sessionEntity.getId(), null);
    }

    private AccessDecision resolveEntry(VehicleEntity vehicle, String plate) {
        LocalDateTime now = LocalDateTime.now();
        ParkingSession session = ParkingSession.forRegisteredUser(UUID.randomUUID(), vehicle.getId(), plate, now);

        ParkingSessionEmbedded embedded = ParkingSessionConverter.toEmbedded(session, now, now);
        ParkingSessionEntity entity = com.parko.persistence.core.converters.ParkingSessionConverter.toEntity(embedded);
        ParkingSessionEntity saved = parkingSessionRepository.save(entity);

        // TODO: validar/reservar contra el artefacto de balance una vez que exista (la barrera igual abre sin saldo, per ADR 0001)
        return new AccessDecision(AccessResult.AUTHORIZED, AccessEventType.ENTRY, saved.getId(), null);
    }

    private String normalize(String identifier) {
        return identifier == null ? "" : identifier.replaceAll("\\s+", "").toUpperCase();
    }
}
