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
