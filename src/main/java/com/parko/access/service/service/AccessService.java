package com.parko.access.service.service;

import com.parko.access.service.converter.AccessLogConverter;
import com.parko.access.service.dto.request.AccessRequest;
import com.parko.access.service.dto.response.AccessResponse;
import com.parko.access.service.strategy.AccessDecision;
import com.parko.access.service.strategy.AccessResolutionStrategy;
import com.parko.domain.lib.model.AccessLog;
import com.parko.domain.lib.model.AccessMethod;
import com.parko.domain.lib.model.AccessResult;
import com.parko.persistence.core.model.embedded.AccessLogEmbedded;
import com.parko.persistence.core.model.entity.AccessLogEntity;
import com.parko.persistence.core.repository.AccessLogRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AccessService {

    private final Map<AccessMethod, AccessResolutionStrategy> strategies;
    private final AccessLogRepository accessLogRepository;
    private final OccupancyService occupancyService;

    public AccessService(List<AccessResolutionStrategy> strategyList, AccessLogRepository accessLogRepository,
                         OccupancyService occupancyService) {
        this.strategies = strategyList.stream()
                .collect(Collectors.toMap(AccessResolutionStrategy::supports, Function.identity()));
        this.accessLogRepository = accessLogRepository;
        this.occupancyService = occupancyService;
    }

    public AccessResponse resolveAccess(AccessRequest request) {
        AccessResolutionStrategy strategy = strategies.get(request.accessMethod());
        if (strategy == null) {
            throw new IllegalArgumentException("No hay estrategia registrada para " + request.accessMethod());
        }

        AccessDecision decision = strategy.resolve(request);
        LocalDateTime occurredAt = request.occurredAt() != null ? request.occurredAt() : LocalDateTime.now();

        AccessLog accessLog = new AccessLog(
                UUID.randomUUID(),
                decision.parkingSessionId(),
                decision.eventType(),
                request.accessMethod(),
                decision.result(),
                request.toString(),
                occurredAt
        );

        LocalDateTime now = LocalDateTime.now();
        AccessLogEmbedded embedded = AccessLogConverter.toEmbedded(accessLog, now, now);
        AccessLogEntity entity = com.parko.persistence.core.converters.AccessLogConverter.toEntity(embedded);
        accessLogRepository.save(entity);

        if (decision.result() == AccessResult.AUTHORIZED) {
            occupancyService.publishCurrent();
        }

        String ticketPdfBase64 = decision.ticketPdf() != null
                ? Base64.getEncoder().encodeToString(decision.ticketPdf())
                : null;
        return new AccessResponse(decision.result(), decision.eventType(), decision.message(), ticketPdfBase64);
    }
}
