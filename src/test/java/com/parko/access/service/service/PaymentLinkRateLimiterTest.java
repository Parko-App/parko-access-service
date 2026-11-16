package com.parko.access.service.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentLinkRateLimiterTest {

    /** Reloj que se mueve a mano para probar la ventana sin esperar. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-15T20:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MutableClock clock = new MutableClock();

    private PaymentLinkRateLimiter limiter(int max) {
        return new PaymentLinkRateLimiter(max, Duration.ofMinutes(1), clock, 100);
    }

    @Test
    void allowsUpToTheMaximum_andRejectsTheNextOne() {
        PaymentLinkRateLimiter limiter = limiter(3);
        UUID ticket = UUID.randomUUID();

        assertThat(limiter.tryAcquire(ticket)).isTrue();
        assertThat(limiter.tryAcquire(ticket)).isTrue();
        assertThat(limiter.tryAcquire(ticket)).isTrue();
        assertThat(limiter.tryAcquire(ticket)).isFalse();
        assertThat(limiter.tryAcquire(ticket)).isFalse();
    }

    @Test
    void eachTicketHasItsOwnLimit() {
        PaymentLinkRateLimiter limiter = limiter(1);

        assertThat(limiter.tryAcquire(UUID.randomUUID())).isTrue();
        assertThat(limiter.tryAcquire(UUID.randomUUID())).isTrue();
    }

    @Test
    void allowsAgain_onceTheWindowHasPassed() {
        PaymentLinkRateLimiter limiter = limiter(2);
        UUID ticket = UUID.randomUUID();
        limiter.tryAcquire(ticket);
        limiter.tryAcquire(ticket);
        assertThat(limiter.tryAcquire(ticket)).isFalse();

        clock.advance(Duration.ofSeconds(61));

        assertThat(limiter.tryAcquire(ticket)).isTrue();
    }

    @Test
    void rejectedAttemptsDoNotExtendTheBlock() {
        PaymentLinkRateLimiter limiter = limiter(1);
        UUID ticket = UUID.randomUUID();
        limiter.tryAcquire(ticket);

        clock.advance(Duration.ofSeconds(30));
        assertThat(limiter.tryAcquire(ticket)).isFalse();
        clock.advance(Duration.ofSeconds(31));

        assertThat(limiter.tryAcquire(ticket)).isTrue();
    }

    @Test
    void aMaximumOfZeroOrLess_disablesTheLimit() {
        PaymentLinkRateLimiter limiter = limiter(0);
        UUID ticket = UUID.randomUUID();

        for (int i = 0; i < 50; i++) {
            assertThat(limiter.tryAcquire(ticket)).isTrue();
        }
    }

    @Test
    void memoryIsBounded_theLeastRecentlyUsedTicketsAreForgotten() {
        PaymentLinkRateLimiter limiter = new PaymentLinkRateLimiter(1, Duration.ofMinutes(1), clock, 2);
        UUID first = UUID.randomUUID();
        limiter.tryAcquire(first);
        limiter.tryAcquire(UUID.randomUUID());
        limiter.tryAcquire(UUID.randomUUID());

        // "first" fue descartado (se superaron los 2 tickets rastreados): vuelve a empezar de cero
        assertThat(limiter.tryAcquire(first)).isTrue();
    }
}
