package com.parko.access.service.controller;

import com.parko.access.service.service.TicketPaymentResolution;
import com.parko.access.service.service.TicketService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/access/sessions")
public class TicketPaymentController {

    private final TicketService ticketService;

    public TicketPaymentController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @GetMapping("/{parkingSessionId}/pay")
    public ResponseEntity<String> pay(@PathVariable UUID parkingSessionId) {
        TicketPaymentResolution resolution = ticketService.resolvePayment(parkingSessionId);
        return switch (resolution.outcome()) {
            case NOT_FOUND -> ResponseEntity.notFound().build();
            case ALREADY_PAID -> ResponseEntity.ok(resolution.message());
            case UNAVAILABLE -> ResponseEntity.status(HttpStatus.CONFLICT).body(resolution.message());
            case REDIRECT -> ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(resolution.checkoutUrl()))
                    .build();
        };
    }
}
