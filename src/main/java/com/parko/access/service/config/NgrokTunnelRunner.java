package com.parko.access.service.config;

import com.ngrok.Forwarder;
import com.ngrok.Session;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.URL;

/**
 * Expone access-service en el dominio público de ngrok para que el visitante
 * pueda abrir desde el celular el link de pago del QR del ticket.
 */
@Component
@ConditionalOnProperty(name = "ngrok.enabled", havingValue = "true")
public class NgrokTunnelRunner {

    private static final Logger log = LoggerFactory.getLogger(NgrokTunnelRunner.class);

    @Value("${server.port:8081}")
    private int serverPort;

    @Value("${ngrok.domain}")
    private String domain;

    private Session session;
    private Forwarder.Endpoint forwarder;

    @EventListener(ApplicationReadyEvent.class)
    public void startTunnel() throws Exception {
        session = Session.withAuthtokenFromEnv().connect();
        forwarder = session.httpEndpoint()
                .domain(domain)
                .forward(new URL("http://localhost:" + serverPort));
        log.info("Tunel ngrok activo en {}", forwarder.getUrl());
    }

    @PreDestroy
    public void stopTunnel() throws Exception {
        if (forwarder != null) {
            forwarder.close();
        }
        if (session != null) {
            session.close();
        }
    }
}
