package com.parko.access.service.dto.externalgateway;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TicketPaymentPreferenceResponse(
        String id,
        @JsonProperty("init_point") String initPoint,
        @JsonProperty("sandbox_init_point") String sandboxInitPoint
) {
}
