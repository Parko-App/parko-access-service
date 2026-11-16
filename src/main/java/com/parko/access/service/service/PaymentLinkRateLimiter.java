package com.parko.access.service.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Limita cuantas veces por ventana se puede pedir el link de pago de un mismo ticket.
 * <p>
 * {@code GET /api/v1/access/sessions/{id}/pay} es publico (lo abre el visitante desde el QR sin cuenta) y cada
 * llamada sobre un ticket pendiente le pide al gateway que cree una preferencia nueva en Mercado Pago. Sin
 * limite, cualquiera con un id valido podia generar preferencias sin tope.
 * <p>
 * El estado es en memoria (una sola instancia de access-service) y esta acotado: solo se registran tickets
 * pendientes que existen y se descartan los menos usados al superar {@value #MAX_TRACKED_TICKETS}.
 * Un maximo de 0 o negativo desactiva el limite.
 */
@Component
public class PaymentLinkRateLimiter {

    static final int MAX_TRACKED_TICKETS = 10_000;

    private final int maxRequests;
    private final Duration window;
    private final Clock clock;
    private final Map<UUID, Deque<Long>> requests;

    @Autowired
    public PaymentLinkRateLimiter(@Value("${access-service.pay-rate-limit.max-requests:5}") int maxRequests,
                                  @Value("${access-service.pay-rate-limit.window:PT1M}") Duration window) {
        this(maxRequests, window, Clock.systemUTC(), MAX_TRACKED_TICKETS);
    }

    PaymentLinkRateLimiter(int maxRequests, Duration window, Clock clock, int maxTrackedTickets) {
        this.maxRequests = maxRequests;
        this.window = window;
        this.clock = clock;
        this.requests = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<UUID, Deque<Long>> eldest) {
                return size() > maxTrackedTickets;
            }
        };
    }

    /** Registra el intento y devuelve true si esta dentro del limite; false si hay que rechazarlo. */
    public synchronized boolean tryAcquire(UUID ticketKey) {
        if (maxRequests <= 0) {
            return true;
        }
        long now = clock.millis();
        long windowStart = now - window.toMillis();
        Deque<Long> hits = requests.computeIfAbsent(ticketKey, key -> new ArrayDeque<>());
        while (!hits.isEmpty() && hits.peekFirst() <= windowStart) {
            hits.pollFirst();
        }
        if (hits.size() >= maxRequests) {
            return false;
        }
        hits.addLast(now);
        return true;
    }
}
