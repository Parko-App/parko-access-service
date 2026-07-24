package com.parko.access.service.strategy;

import com.parko.access.service.dto.request.AccessRequest;
import com.parko.domain.lib.model.AccessMethod;

public interface AccessResolutionStrategy {

    AccessMethod supports();

    AccessDecision resolve(AccessRequest request);
}
