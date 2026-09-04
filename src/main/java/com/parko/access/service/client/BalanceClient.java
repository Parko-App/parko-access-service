package com.parko.access.service.client;

import com.parko.access.service.dto.balance.ChargeRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

@FeignClient(
        name = "balance-service",
        url = "${balance-service.base-url}"
)
public interface BalanceClient {

    @PostMapping("/api/v1/balance/charge")
    UUID charge(@RequestBody ChargeRequest request);
}
