package com.parko.access.service.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.JacksonJavaTypeMapper;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String ACCESS_EXCHANGE = "access.exchange";

    public static final String PAYMENT_CONFIRMED_QUEUE = "access.payment-confirmed.queue";
    public static final String PAYMENT_CONFIRMED_ROUTING_KEY = "access.payment.confirmed";

    public static final String PAYMENT_FAILED_QUEUE = "access.payment-failed.queue";
    public static final String PAYMENT_FAILED_ROUTING_KEY = "access.payment.failed";

    public static final String DEAD_LETTER_EXCHANGE = "access.dlx.exchange";
    public static final String PAYMENT_CONFIRMED_DLQ = "access.payment-confirmed.dlq";
    public static final String PAYMENT_FAILED_DLQ = "access.payment-failed.dlq";

    public static final long MESSAGE_TTL_MS = 86_400_000L;

    @Bean
    public DirectExchange accessExchange() {
        return new DirectExchange(ACCESS_EXCHANGE);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE);
    }

    @Bean
    public Queue paymentConfirmedQueue() {
        return QueueBuilder.durable(PAYMENT_CONFIRMED_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", PAYMENT_CONFIRMED_DLQ)
                .withArgument("x-message-ttl", MESSAGE_TTL_MS)
                .build();
    }

    @Bean
    public Queue paymentFailedQueue() {
        return QueueBuilder.durable(PAYMENT_FAILED_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", PAYMENT_FAILED_DLQ)
                .withArgument("x-message-ttl", MESSAGE_TTL_MS)
                .build();
    }

    @Bean
    public Queue paymentConfirmedDeadLetterQueue() {
        return new Queue(PAYMENT_CONFIRMED_DLQ, true);
    }

    @Bean
    public Queue paymentFailedDeadLetterQueue() {
        return new Queue(PAYMENT_FAILED_DLQ, true);
    }

    @Bean
    public Binding paymentConfirmedBinding(Queue paymentConfirmedQueue, DirectExchange accessExchange) {
        return BindingBuilder.bind(paymentConfirmedQueue).to(accessExchange).with(PAYMENT_CONFIRMED_ROUTING_KEY);
    }

    @Bean
    public Binding paymentFailedBinding(Queue paymentFailedQueue, DirectExchange accessExchange) {
        return BindingBuilder.bind(paymentFailedQueue).to(accessExchange).with(PAYMENT_FAILED_ROUTING_KEY);
    }

    @Bean
    public Binding paymentConfirmedDeadLetterBinding(Queue paymentConfirmedDeadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(paymentConfirmedDeadLetterQueue).to(deadLetterExchange).with(PAYMENT_CONFIRMED_DLQ);
    }

    @Bean
    public Binding paymentFailedDeadLetterBinding(Queue paymentFailedDeadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(paymentFailedDeadLetterQueue).to(deadLetterExchange).with(PAYMENT_FAILED_DLQ);
    }

    @Bean
    public MessageConverter messageConverter() {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();
        // ignora el __TypeId__ del productor (apunta a la clase de external-gateway, no existe acá)
        converter.setTypePrecedence(JacksonJavaTypeMapper.TypePrecedence.INFERRED);
        return converter;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
