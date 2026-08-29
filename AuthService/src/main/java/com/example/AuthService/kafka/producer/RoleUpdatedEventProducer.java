package com.example.AuthService.kafka.producer;

import com.project.events.UpdatingRoleEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RoleUpdatedEventProducer {

    private final KafkaTemplate<String, UpdatingRoleEvent> kafkaTemplate;

    private static final String TOPIC = "roles.update";
    public void publishUpdatedRoleEvent(UpdatingRoleEvent event) {
        kafkaTemplate.send(TOPIC, event);
        System.out.println("UpdatingRoleEvent  Published Successfully   to Kafka Topic: " + TOPIC);
    }
}
