package com.parko.access.service.service;

import com.parko.access.service.dto.response.OccupancyResponse;
import com.parko.domain.lib.model.SessionStatus;
import com.parko.persistence.core.repository.ParkingSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

@Service
public class OccupancyService {

    private static final Logger log = LoggerFactory.getLogger(OccupancyService.class);
    private static final String OCCUPANCY_EVENT = "occupancy";
    private static final String HEARTBEAT_COMMENT = "ping";

    private final ParkingSessionRepository parkingSessionRepository;
    private final long capacity;
    private final Set<SseEmitter> emitters = new CopyOnWriteArraySet<>();

    public OccupancyService(ParkingSessionRepository parkingSessionRepository,
                            @Value("${parking.capacity}") long capacity) {
        this.parkingSessionRepository = parkingSessionRepository;
        this.capacity = capacity;
    }

    public OccupancyResponse getCurrent() {
        long occupied = parkingSessionRepository.countByStatus(SessionStatus.ACTIVE);
        return new OccupancyResponse(occupied, capacity, Math.max(0, capacity - occupied));
    }

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(emitter::complete);
        emitter.onError(error -> emitters.remove(emitter));
        send(emitter, SseEmitter.event().name(OCCUPANCY_EVENT).data(getCurrent(), MediaType.APPLICATION_JSON));
        return emitter;
    }

    public void publishCurrent() {
        if (emitters.isEmpty()) {
            return;
        }
        try {
            SseEmitter.SseEventBuilder event =
                    SseEmitter.event().name(OCCUPANCY_EVENT).data(getCurrent(), MediaType.APPLICATION_JSON);
            emitters.forEach(emitter -> send(emitter, event));
        } catch (RuntimeException e) {
            // la ocupación es informativa: un fallo acá no debe romper el flujo de acceso
            log.error("Fallo al publicar la ocupación: {}", e.getMessage(), e);
        }
    }

    @Scheduled(fixedRate = 30_000)
    public void sendHeartbeat() {
        emitters.forEach(emitter -> send(emitter, SseEmitter.event().comment(HEARTBEAT_COMMENT)));
    }

    private void send(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException e) {
            emitters.remove(emitter);
            emitter.completeWithError(e);
        }
    }
}
