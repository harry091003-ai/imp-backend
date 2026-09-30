package com.project.kafka.config;

import com.project.kafka.trace.TraceIdProducerInterceptor;
import com.project.kafka.trace.TraceIdRecordInterceptor;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.RecordInterceptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

// Does Spring wire it up by itself, the way a real service gets it? No broker is needed:
// producers and consumers connect lazily, so a context can start with nothing listening.
class KafkaTraceIdAutoConfigTest {

    private static final String OURS = TraceIdProducerInterceptor.class.getName();

    // Same auto-configs a real service has: Spring Boot's own Kafka one + both of ours.
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    KafkaAutoConfiguration.class,
                    KafkaErrorHandlingAutoConfig.class,
                    KafkaTraceIdAutoConfig.class))
            .withPropertyValues("spring.kafka.bootstrap-servers=localhost:1");

    // ---------------- sending ----------------

    @Test
    void theProducerFactoryGetsTheInterceptor() {
        runner.run(context -> {
            DefaultKafkaProducerFactory<?, ?> factory = (DefaultKafkaProducerFactory<?, ?>) context.getBean(ProducerFactory.class);
            Object configured = factory.getConfigurationProperties().get("interceptor.classes");
            assertEquals(OURS, configured);
        });
    }

    // A service that already has its own producer interceptor keeps it. Ours is ADDED.
    @Test
    void existingProducerInterceptorsAreKept() {
        runner.withPropertyValues("spring.kafka.producer.properties.interceptor.classes=com.example.Other")
                .run(context -> {
                    DefaultKafkaProducerFactory<?, ?> factory = (DefaultKafkaProducerFactory<?, ?>) context.getBean(ProducerFactory.class);
                    String configured = String.valueOf(factory.getConfigurationProperties().get("interceptor.classes"));
                    assertTrue(configured.contains("com.example.Other"), configured);
                    assertTrue(configured.contains(OURS), configured);
                });
    }

    // The merge itself, for every shape "interceptor.classes" can arrive in.
    @Test
    void mergingKeepsWhatIsThereAndNeverAddsOursTwice() {
        assertEquals(OURS, KafkaTraceIdAutoConfig.withTraceInterceptor(null));
        assertEquals(OURS, KafkaTraceIdAutoConfig.withTraceInterceptor("  "));
        assertEquals("a.B," + OURS, KafkaTraceIdAutoConfig.withTraceInterceptor("a.B"));
        assertEquals("a.B," + OURS, KafkaTraceIdAutoConfig.withTraceInterceptor("a.B," + OURS), "already there: unchanged");
        assertEquals(List.of("a.B", OURS), KafkaTraceIdAutoConfig.withTraceInterceptor(List.of("a.B")));
        assertEquals(List.of("a.B", OURS), KafkaTraceIdAutoConfig.withTraceInterceptor(List.of("a.B", OURS)), "already there: unchanged");
        assertEquals(List.of(String.class, OURS), KafkaTraceIdAutoConfig.withTraceInterceptor(String.class));
    }

    // ---------------- receiving ----------------

    @Test
    void theReceiverInterceptorIsRegisteredAndSpringBootPutsItOnTheListenerContainerFactory() {
        runner.run(context -> {
            assertInstanceOf(TraceIdRecordInterceptor.class, context.getBean(RecordInterceptor.class));

            ConcurrentKafkaListenerContainerFactory<?, ?> factory = context.getBean(ConcurrentKafkaListenerContainerFactory.class);
            Object applied = ReflectionTestUtils.getField(factory, "recordInterceptor");
            assertInstanceOf(TraceIdRecordInterceptor.class, applied,
                    "Spring Boot must have put our interceptor on the factory that builds every @KafkaListener container");
        });
    }

    // Spring Boot accepts only ONE RecordInterceptor. A service with its own keeps it.
    @Test
    void aServicesOwnRecordInterceptorWins() {
        runner.withUserConfiguration(OwnInterceptorConfig.class).run(context -> {
            assertEquals(1, context.getBeansOfType(RecordInterceptor.class).size());
            assertInstanceOf(OwnInterceptor.class, context.getBean(RecordInterceptor.class));
        });
    }

    // ---------------- the startup bug this step found (introduced by B2) ----------------
    // common-kafka registers 2 dead-letter KafkaTemplate beans. Spring Boot creates its OWN default
    // "kafkaTemplate" only if no KafkaTemplate exists yet, and our auto-config used to run first
    // (alphabetical order), so Boot's default was never created. Every producer that injects
    // KafkaTemplate<String,Object> (ReportingService, Ai-Service) then failed to start.
    // KafkaErrorHandlingAutoConfig now runs AFTER Boot's Kafka auto-config.

    @Test
    void bootsDefaultTemplateIsNotSuppressedByTheDeadLetterTemplates() {
        runner.run(context -> {
            assertTrue(context.containsBean("kafkaTemplate"), "Spring Boot's own default template must exist");
            assertTrue(context.containsBean("dltRawBytesTemplate"));
            assertTrue(context.containsBean("dltJsonTemplate"));
            // ...and B2's safety net still gets the right two templates, by name, among three.
            assertNotNull(context.getBean(DeadLetterPublishingRecoverer.class));
            assertNotNull(context.getBean(DefaultErrorHandler.class));
        });
    }

    // With THREE KafkaTemplate beans around now, B2's dead-letter recoverer must still get the
    // RIGHT two, picked by name. If it got Boot's default one (or swapped them), a poison message
    // would be sent through the wrong serializer and B2's whole safety net would break.
    @Test
    void theDeadLetterRecovererStillUsesItsOwnTwoTemplates() {
        runner.run(context -> {
            DeadLetterPublishingRecoverer recoverer = context.getBean(DeadLetterPublishingRecoverer.class);
            @SuppressWarnings("unchecked")
            Function<ProducerRecord<?, ?>, KafkaOperations<?, ?>> pickTemplate =
                    (Function<ProducerRecord<?, ?>, KafkaOperations<?, ?>>) ReflectionTestUtils.getField(recoverer, "templateResolver");

            assertSame(context.getBean("dltRawBytesTemplate"),
                    pickTemplate.apply(new ProducerRecord<>("t", new byte[]{1, 2, 3})),
                    "messages that failed to DESERIALIZE (raw bytes) must go through the raw-bytes template");
            assertSame(context.getBean("dltJsonTemplate"),
                    pickTemplate.apply(new ProducerRecord<>("t", "an ordinary object")),
                    "every other failure must go through the JSON template");
        });
    }

    // Producers written the way the real services write them. AuthService uses a SPECIFIC event
    // type (KafkaTemplate<String, UserRegisteredEvent>); Incident, Reporting and Ai use
    // KafkaTemplate<String, Object>. Both must still start with common-kafka on the classpath.
    @Test
    void producersWrittenLikeTheRealServicesStillStart() {
        runner.withUserConfiguration(AuthLikeProducer.class, IncidentLikeProducer.class).run(context -> {
            assertNull(context.getStartupFailure());
            assertNotNull(context.getBean(AuthLikeProducer.class).template);
            assertNotNull(context.getBean(IncidentLikeProducer.class).template);
        });
    }

    // ---------------- helpers ----------------

    record SomeEvent(String id) {
    }

    // Like AuthService's producers: a KafkaTemplate with a SPECIFIC event type.
    static class AuthLikeProducer {
        final KafkaTemplate<String, SomeEvent> template;

        AuthLikeProducer(KafkaTemplate<String, SomeEvent> template) {
            this.template = template;
        }
    }

    // Like IncidentService's producer: KafkaTemplate<String, Object>.
    static class IncidentLikeProducer {
        final KafkaTemplate<String, Object> template;

        IncidentLikeProducer(KafkaTemplate<String, Object> template) {
            this.template = template;
        }
    }

    static class OwnInterceptor implements RecordInterceptor<Object, Object> {
        @Override
        public ConsumerRecord<Object, Object> intercept(ConsumerRecord<Object, Object> record, Consumer<Object, Object> consumer) {
            return record;
        }
    }

    @Configuration
    static class OwnInterceptorConfig {
        @Bean
        RecordInterceptor<Object, Object> ownRecordInterceptor() {
            return new OwnInterceptor();
        }
    }
}
