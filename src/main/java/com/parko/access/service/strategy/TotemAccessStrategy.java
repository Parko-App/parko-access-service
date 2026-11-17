package com.parko.access.service.strategy;

import com.parko.access.service.dto.request.AccessRequest;
import com.parko.access.service.service.VisitorAccessService;
import com.parko.domain.lib.model.AccessEventType;
import com.parko.domain.lib.model.AccessMethod;
import com.parko.domain.lib.model.AccessResult;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TotemAccessStrategy implements AccessResolutionStrategy {

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private final VisitorAccessService visitorAccessService;

    public TotemAccessStrategy(VisitorAccessService visitorAccessService) {
        this.visitorAccessService = visitorAccessService;
    }

    @Override
    public AccessMethod supports() {
        return AccessMethod.TICKET;
    }

    @Override
    public AccessDecision resolve(AccessRequest request) {
        String identifier = request.identifier();
        if (identifier == null || identifier.isBlank()) {
            return visitorAccessService.enter(normalizePlate(request.visitorPlate()));
        }
        return resolveExit(identifier.trim());
    }

    private AccessDecision resolveExit(String identifier) {
        UUID parkingSessionId = extractParkingSessionId(identifier);
        if (parkingSessionId == null) {
            return new AccessDecision(AccessResult.DENIED, AccessEventType.EXIT, null, "Ticket inválido: " + identifier, null);
        }
        return visitorAccessService.exit(parkingSessionId);
    }

    private UUID extractParkingSessionId(String identifier) {
        Matcher matcher = UUID_PATTERN.matcher(identifier);
        if (!matcher.find()) {
            return null;
        }
        try {
            return UUID.fromString(matcher.group());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String normalizePlate(String plate) {
        if (plate == null || plate.isBlank()) {
            return null;
        }
        return plate.replaceAll("\\s+", "").toUpperCase();
    }
}
