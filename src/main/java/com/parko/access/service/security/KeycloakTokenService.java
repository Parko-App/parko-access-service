package com.parko.access.service.security;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;

@Component
public class KeycloakTokenService {

    private static final long REFRESH_MARGIN_SECONDS = 10;

    private final RestTemplate restTemplate = new RestTemplate();
    private final String tokenUri;
    private final String clientId;
    private final String clientSecret;

    private volatile String cachedToken;
    private volatile Instant expiresAt = Instant.EPOCH;

    public KeycloakTokenService(
            @Value("${keycloak.token-uri}") String tokenUri,
            @Value("${keycloak.client-id}") String clientId,
            @Value("${keycloak.client-secret}") String clientSecret) {
        this.tokenUri = tokenUri;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public synchronized String getToken() {
        if (cachedToken != null && Instant.now().isBefore(expiresAt.minusSeconds(REFRESH_MARGIN_SECONDS))) {
            return cachedToken;
        }

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        TokenResponse response = restTemplate.postForObject(tokenUri, new HttpEntity<>(body, headers), TokenResponse.class);

        cachedToken = response.accessToken();
        expiresAt = Instant.now().plusSeconds(response.expiresIn());
        return cachedToken;
    }

    private record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") long expiresIn) {
    }
}
