package com.parko.access.service.client;

import com.parko.access.service.dto.externalgateway.TicketPaymentPreferenceResponse;
import com.parko.access.service.dto.externalgateway.TicketPaymentRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "external-gateway",
        url = "${external-gateway.base-url}",
        configuration = ExternalGatewayClientFeignConfig.class
)
public interface ExternalGatewayClient {

    @PostMapping("/internal/ticket-payments")
    TicketPaymentPreferenceResponse createTicketPayment(@RequestBody TicketPaymentRequest request);
}
