package com.example.AuthService.kafka.producer;

import com.project.events.UpdatingRoleEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RoleUpdatedEventProducer {

    private static final Logger log = LoggerFactory.getLogger(RoleUpdatedEventProducer.class);

    private final KafkaTemplate<String, UpdatingRoleEvent> kafkaTemplate;

    private static final String TOPIC = "roles.update";
    public void publishUpdatedRoleEvent(UpdatingRoleEvent event) {
        kafkaTemplate.send(TOPIC, event);
        // C1 step 1: was System.out.println("... Published Successfully ...").
        // Reworded: send() only HANDS the event to Kafka. Kafka confirms later, and could still fail.
        log.debug("UpdatingRoleEvent handed to Kafka, topic {}", TOPIC);
    }
}
