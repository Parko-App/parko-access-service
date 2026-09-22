package com.parko.access.service.consumer;

import com.parko.access.service.config.RabbitConfig;
import com.parko.access.service.event.TicketPaymentConfirmedMessage;
import com.parko.domain.lib.model.TicketStatus;
import com.parko.persistence.core.model.entity.TicketEntity;
import com.parko.persistence.core.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;

@Component
public class TicketPaymentConfirmedConsumer {

    private static final Logger log = LoggerFactory.getLogger(TicketPaymentConfirmedConsumer.class);

    private final TicketRepository ticketRepository;

    public TicketPaymentConfirmedConsumer(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    @RabbitListener(queues = RabbitConfig.PAYMENT_CONFIRMED_QUEUE)
    public void onPaymentConfirmed(TicketPaymentConfirmedMessage message) {
        TicketEntity ticket = ticketRepository.findByParkingSession_Id(message.parkingSessionId())
                .orElseThrow(() -> new NoSuchElementException(
                        "Ticket no encontrado para parkingSessionId: " + message.parkingSessionId()));

        if (ticket.getStatus() != TicketStatus.PENDING_PAYMENT) {
            log.warn("Ticket para parkingSessionId={} ya está en estado {}, se ignora confirmación de pago duplicada",
                    message.parkingSessionId(), ticket.getStatus());
            return;
        }

        LocalDateTime paidAt = LocalDateTime.now();
        ticket.setStatus(TicketStatus.PAID);
        ticket.setPaidAt(paidAt);
        ticket.setUpdatedAt(paidAt);
        ticketRepository.save(ticket);

        log.info("Ticket marcado como PAID para parkingSessionId={}, amount={}",
                message.parkingSessionId(), message.amount());
    }
}
