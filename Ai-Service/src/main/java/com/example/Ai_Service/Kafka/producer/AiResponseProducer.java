package com.example.Ai_Service.Kafka.producer;

import com.project.events.AiProcessedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiResponseProducer {

    private final KafkaTemplate<String,Object> kafkaTemplate;
    private static final String AI_CLASSIFICATION_RESULT_TOPIC = "ai.classification.result";

    public void publishClassificationResult(AiProcessedEvent event) {

    kafkaTemplate.send(AI_CLASSIFICATION_RESULT_TOPIC,event.getIncidentId().toString(),event);

    }

}
