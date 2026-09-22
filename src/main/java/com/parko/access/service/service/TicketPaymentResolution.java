package com.parko.access.service.service;

import com.parko.domain.lib.model.TicketStatus;

public record TicketPaymentResolution(Outcome outcome, String message, String checkoutUrl) {

    public enum Outcome {
        NOT_FOUND,
        ALREADY_PAID,
        UNAVAILABLE,
        REDIRECT
    }

    public static TicketPaymentResolution notFound() {
        return new TicketPaymentResolution(Outcome.NOT_FOUND, null, null);
    }

    public static TicketPaymentResolution alreadyPaid() {
        return new TicketPaymentResolution(Outcome.ALREADY_PAID, "Ticket ya pagado. Podés salir.", null);
    }

    public static TicketPaymentResolution unavailable(TicketStatus status) {
        return new TicketPaymentResolution(Outcome.UNAVAILABLE,
                "Ticket no disponible para pago (estado: " + status + ").", null);
    }

    public static TicketPaymentResolution redirect(String checkoutUrl) {
        return new TicketPaymentResolution(Outcome.REDIRECT, null, checkoutUrl);
    }
}
