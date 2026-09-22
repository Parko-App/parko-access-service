package com.parko.access.service.client;

import com.parko.access.service.security.KeycloakTokenService;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;

public class ExternalGatewayClientFeignConfig {

    @Bean
    public RequestInterceptor externalGatewayAuthInterceptor(KeycloakTokenService keycloakTokenService) {
        return template -> template.header("Authorization", "Bearer " + keycloakTokenService.getToken());
    }
}
