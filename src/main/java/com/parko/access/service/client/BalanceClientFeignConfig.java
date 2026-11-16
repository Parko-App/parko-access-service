package com.parko.access.service.client;

import com.parko.access.service.security.KeycloakTokenService;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;

public class BalanceClientFeignConfig {

    @Bean
    public RequestInterceptor balanceAuthInterceptor(KeycloakTokenService keycloakTokenService) {
        return template -> template.header("Authorization", "Bearer " + keycloakTokenService.getToken());
    }
}
