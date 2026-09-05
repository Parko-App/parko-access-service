package com.parko.access.service.strategy;

import com.parko.access.service.dto.request.AccessRequest;
import com.parko.domain.lib.model.AccessMethod;
import org.springframework.stereotype.Component;

@Component
public class TotemAccessStrategy implements AccessResolutionStrategy {

    @Override
    public AccessMethod supports() {
        return AccessMethod.TICKET;
    }

    @Override
    public AccessDecision resolve(AccessRequest request) {
        throw new UnsupportedOperationException("No implementado todavía");
    }

    private AccessDecision resolveEntry(AccessRequest request) {
        throw new UnsupportedOperationException("No implementado todavía");
    }

    private AccessDecision resolveExit(AccessRequest request) {
        throw new UnsupportedOperationException("No implementado todavía");
    }
}
