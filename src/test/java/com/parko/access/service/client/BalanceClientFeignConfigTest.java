package com.parko.access.service.client;

import com.parko.access.service.security.KeycloakTokenService;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BalanceClientFeignConfigTest {

    @Mock
    private KeycloakTokenService keycloakTokenService;

    @Test
    void balanceAuthInterceptor_addsBearerAuthorizationHeader() {
        when(keycloakTokenService.getToken()).thenReturn("TEST-token-123");
        BalanceClientFeignConfig config = new BalanceClientFeignConfig();

        RequestInterceptor interceptor = config.balanceAuthInterceptor(keycloakTokenService);
        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        assertThat(template.headers().get("Authorization")).containsExactly("Bearer TEST-token-123");
    }
}
