package com.parko.access.service.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KeycloakTokenServiceTest {

    private static final String TOKEN_URI = "http://keycloak/realms/Parko/protocol/openid-connect/token";

    private MockRestServiceServer mockServer;
    private KeycloakTokenService service;

    @BeforeEach
    void setUp() {
        service = new KeycloakTokenService(TOKEN_URI, "client-1", "secret-1");
        RestTemplate restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.bindTo(restTemplate).build();
        ReflectionTestUtils.setField(service, "restTemplate", restTemplate);
    }

    @Test
    void getToken_requestsClientCredentialsGrantAndReturnsAccessToken() {
        mockServer.expect(requestTo(TOKEN_URI))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(containsString("grant_type=client_credentials")))
                .andExpect(content().string(containsString("client_id=client-1")))
                .andExpect(content().string(containsString("client_secret=secret-1")))
                .andRespond(withSuccess("{\"access_token\":\"tok-1\",\"expires_in\":3600}", MediaType.APPLICATION_JSON));

        String token = service.getToken();

        assertThat(token).isEqualTo("tok-1");
        mockServer.verify();
    }

    @Test
    void getToken_reusesCachedToken_whileFarFromExpiry() {
        mockServer.expect(times(1), requestTo(TOKEN_URI))
                .andRespond(withSuccess("{\"access_token\":\"tok-1\",\"expires_in\":3600}", MediaType.APPLICATION_JSON));

        String first = service.getToken();
        String second = service.getToken();

        assertThat(first).isEqualTo("tok-1");
        assertThat(second).isEqualTo("tok-1");
        mockServer.verify();
    }

    @Test
    void getToken_requestsNewToken_whenCachedOneIsNearExpiry() {
        mockServer.expect(times(2), requestTo(TOKEN_URI))
                .andRespond(withSuccess("{\"access_token\":\"tok-1\",\"expires_in\":5}", MediaType.APPLICATION_JSON));

        service.getToken();
        service.getToken();

        mockServer.verify();
    }
}
