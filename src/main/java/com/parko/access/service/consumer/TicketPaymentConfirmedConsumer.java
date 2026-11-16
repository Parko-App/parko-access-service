package com.parko.access.service.consumer;

import com.parko.access.service.config.RabbitConfig;
import com.parko.access.service.event.TicketPaymentConfirmedMessage;
import com.parko.domain.lib.model.TicketStatus;
import com.parko.domain.lib.model.TransactionStatus;
import com.parko.domain.lib.model.TransactionType;
import com.parko.persistence.core.model.entity.TicketEntity;
import com.parko.persistence.core.model.entity.TransactionEntity;
import com.parko.persistence.core.repository.TicketRepository;
import com.parko.persistence.core.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.UUID;

@Component
public class TicketPaymentConfirmedConsumer {

    private static final Logger log = LoggerFactory.getLogger(TicketPaymentConfirmedConsumer.class);

    static final String PAYMENT_PROVIDER = "MERCADO_PAGO";

    private final TicketRepository ticketRepository;
    private final TransactionRepository transactionRepository;

    public TicketPaymentConfirmedConsumer(TicketRepository ticketRepository,
                                          TransactionRepository transactionRepository) {
        this.ticketRepository = ticketRepository;
        this.transactionRepository = transactionRepository;
    }

    @RabbitListener(queues = RabbitConfig.PAYMENT_CONFIRMED_QUEUE)
    @Transactional
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

        TransactionEntity payment = new TransactionEntity();
        payment.setId(UUID.randomUUID());
        payment.setParkingSession(ticket.getParkingSession());
        payment.setType(TransactionType.CHARGE);
        payment.setStatus(TransactionStatus.COMPLETED);
        payment.setAmount(message.amount());
        payment.setPaymentProvider(PAYMENT_PROVIDER);
        payment.setCreatedAt(paidAt);
        payment.setUpdatedAt(paidAt);
        transactionRepository.save(payment);

        log.info("Ticket marcado como PAID para parkingSessionId={}, amount={}",
                message.parkingSessionId(), message.amount());
    }
}
