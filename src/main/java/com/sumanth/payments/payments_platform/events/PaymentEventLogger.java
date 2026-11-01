package com.sumanth.payments.payments_platform.events;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class PaymentEventLogger {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventLogger.class);

    @KafkaListener(topics = OutboxPublisher.TOPIC, groupId = "payments-logger")
    public void onEvent(ConsumerRecord<String, String> record) {
        Header h = record.headers().lastHeader("event_type");
        String type = h == null ? "unknown" : new String(h.value(), StandardCharsets.UTF_8);
        log.info("EVENT {} key={} partition={} offset={} payload={}",
                type, record.key(), record.partition(), record.offset(), record.value());
    }
}