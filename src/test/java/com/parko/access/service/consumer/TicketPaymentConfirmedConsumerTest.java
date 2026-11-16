package com.parko.access.service.consumer;

import com.parko.access.service.event.TicketPaymentConfirmedMessage;
import com.parko.domain.lib.model.TicketStatus;
import com.parko.persistence.core.model.entity.ParkingSessionEntity;
import com.parko.domain.lib.model.TransactionStatus;
import com.parko.domain.lib.model.TransactionType;
import com.parko.persistence.core.model.entity.TicketEntity;
import com.parko.persistence.core.model.entity.TransactionEntity;
import com.parko.persistence.core.repository.TicketRepository;
import com.parko.persistence.core.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketPaymentConfirmedConsumerTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TransactionRepository transactionRepository;

    private TicketPaymentConfirmedConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new TicketPaymentConfirmedConsumer(ticketRepository, transactionRepository);
    }

    private TicketEntity ticketWith(UUID parkingSessionId, TicketStatus status) {
        ParkingSessionEntity parkingSession = new ParkingSessionEntity();
        parkingSession.setId(parkingSessionId);

        TicketEntity ticket = new TicketEntity();
        ticket.setId(UUID.randomUUID());
        ticket.setParkingSession(parkingSession);
        ticket.setTicketNumber("TCK-ABCDEF12");
        ticket.setQrData("http://localhost:8081/api/v1/access/sessions/" + parkingSessionId + "/pay");
        ticket.setStatus(status);
        ticket.setIssuedAt(LocalDateTime.now().minusMinutes(10));
        ticket.setCreatedAt(LocalDateTime.now().minusMinutes(10));
        ticket.setUpdatedAt(LocalDateTime.now().minusMinutes(10));
        return ticket;
    }

    @Test
    void onPaymentConfirmed_marksTicketAsPaid() {
        UUID parkingSessionId = UUID.randomUUID();
        TicketEntity ticket = ticketWith(parkingSessionId, TicketStatus.PENDING_PAYMENT);
        when(ticketRepository.findByParkingSession_Id(parkingSessionId)).thenReturn(Optional.of(ticket));

        consumer.onPaymentConfirmed(new TicketPaymentConfirmedMessage(parkingSessionId, BigDecimal.valueOf(3000)));

        ArgumentCaptor<TicketEntity> captor = ArgumentCaptor.forClass(TicketEntity.class);
        verify(ticketRepository).save(captor.capture());
        TicketEntity saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(TicketStatus.PAID);
        assertThat(saved.getPaidAt()).isNotNull();
    }

    @Test
    void onPaymentConfirmed_recordsCompletedChargeTransaction() {
        UUID parkingSessionId = UUID.randomUUID();
        TicketEntity ticket = ticketWith(parkingSessionId, TicketStatus.PENDING_PAYMENT);
        when(ticketRepository.findByParkingSession_Id(parkingSessionId)).thenReturn(Optional.of(ticket));

        consumer.onPaymentConfirmed(new TicketPaymentConfirmedMessage(parkingSessionId, BigDecimal.valueOf(3000)));

        ArgumentCaptor<TransactionEntity> captor = ArgumentCaptor.forClass(TransactionEntity.class);
        verify(transactionRepository).save(captor.capture());
        TransactionEntity payment = captor.getValue();
        assertThat(payment.getId()).isNotNull();
        assertThat(payment.getParkingSession()).isSameAs(ticket.getParkingSession());
        assertThat(payment.getBalanceAccount()).isNull();
        assertThat(payment.getType()).isEqualTo(TransactionType.CHARGE);
        assertThat(payment.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(payment.getAmount()).isEqualByComparingTo("3000");
        assertThat(payment.getPaymentProvider()).isEqualTo("MERCADO_PAGO");
        assertThat(payment.getCreatedAt()).isEqualTo(ticket.getPaidAt());
    }

    @Test
    void onPaymentConfirmed_throws_whenTicketNotFound() {
        UUID parkingSessionId = UUID.randomUUID();
        when(ticketRepository.findByParkingSession_Id(parkingSessionId)).thenReturn(Optional.empty());

        TicketPaymentConfirmedMessage message = new TicketPaymentConfirmedMessage(parkingSessionId, BigDecimal.valueOf(3000));

        assertThatThrownBy(() -> consumer.onPaymentConfirmed(message))
                .isInstanceOf(NoSuchElementException.class);

        verify(ticketRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void onPaymentConfirmed_ignoresDuplicate_whenTicketAlreadyPaid() {
        UUID parkingSessionId = UUID.randomUUID();
        TicketEntity ticket = ticketWith(parkingSessionId, TicketStatus.PAID);
        when(ticketRepository.findByParkingSession_Id(parkingSessionId)).thenReturn(Optional.of(ticket));

        consumer.onPaymentConfirmed(new TicketPaymentConfirmedMessage(parkingSessionId, BigDecimal.valueOf(3000)));

        verify(ticketRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }
}
