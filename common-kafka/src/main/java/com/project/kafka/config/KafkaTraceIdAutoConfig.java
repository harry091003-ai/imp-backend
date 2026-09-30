package com.project.kafka.config;

import com.project.kafka.trace.TraceIdProducerInterceptor;
import com.project.kafka.trace.TraceIdRecordInterceptor;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.kafka.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.RecordInterceptor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

// C1 step 3: carries the trace id through Kafka, in every service that uses common-kafka.
// It adds two things, and neither needs a setting in any service:
//
//   SENDING   -> a customizer that makes Spring Boot's producer stick the trace id on every
//                outgoing message (TraceIdProducerInterceptor)
//   RECEIVING -> a RecordInterceptor bean. Spring Boot picks it up by itself and puts it on the
//                listener container (TraceIdRecordInterceptor)
//
// Why not add a traceId field to each event class instead? There is no shared base event class
// (15 separate events) and about 15 send() calls. A message header is the Kafka twin of an HTTP
// header - metadata on the envelope, not part of the letter - and it is set in ONE place.
@AutoConfiguration
@ConditionalOnClass(KafkaTemplate.class)
public class KafkaTraceIdAutoConfig {

    // Spring Boot calls this on the producer factory it builds. We add our interceptor to it.
    @Bean
    @ConditionalOnMissingBean(name = "traceIdProducerFactoryCustomizer")
    public DefaultKafkaProducerFactoryCustomizer traceIdProducerFactoryCustomizer() {
        return factory -> factory.updateConfigs(Map.of(
                ProducerConfig.INTERCEPTOR_CLASSES_CONFIG,
                withTraceInterceptor(factory.getConfigurationProperties().get(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG))));
    }

    // Spring Boot accepts only ONE RecordInterceptor. If a service defines its own, theirs wins.
    @Bean
    @ConditionalOnMissingBean(RecordInterceptor.class)
    public RecordInterceptor<Object, Object> traceIdRecordInterceptor() {
        return new TraceIdRecordInterceptor<>();
    }

    // "interceptor.classes" may already hold something (a String, or a list). ADD ours, never
    // replace what is there.
    static Object withTraceInterceptor(Object existing) {
        String ours = TraceIdProducerInterceptor.class.getName();
        if (existing == null) {
            return ours;
        }
        if (existing instanceof String text) {
            if (text.contains(ours)) {
                return text;
            }
            return text.isBlank() ? ours : text + "," + ours;
        }
        if (existing instanceof Collection<?> collection) {
            List<Object> all = new ArrayList<>(collection);
            if (!all.contains(ours) && !all.contains(TraceIdProducerInterceptor.class)) {
                all.add(ours);
            }
            return all;
        }
        return List.of(existing, ours);
    }
}
