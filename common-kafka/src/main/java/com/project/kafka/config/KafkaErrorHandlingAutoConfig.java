package com.project.kafka.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The shared Kafka safety net. A service gets all of this just by adding the common-kafka
 * dependency - there is nothing to configure in the service itself.
 *
 * WHAT PROBLEM THIS SOLVES
 * When a Kafka listener throws, Kafka's default is to hand the same message back and try again,
 * forever. That sounds safe but it is a trap: a Kafka partition is a strict queue, so a message
 * that can never succeed blocks every message behind it, permanently. Notifications, reports or
 * user updates would silently stop completely, with no error to tell you.
 *
 * WHAT WE DO INSTEAD
 *   1. Retry 3 times, 1 second apart. This fixes temporary problems like a database blip.
 *   2. If it still fails, publish a copy to a "dead letter topic" named <topic>-dlt.
 *   3. Mark the original as done, so the queue keeps moving.
 * The bad message is parked where you can look at it. Everything else keeps working.
 *
 * ONE THING THAT IS EASY TO MISS
 * A message can fail in two places:
 *   A) Inside your listener  - database down, business rule failed.  Handled here.
 *   B) Before your listener  - the JSON could not be turned into a Java object at all.
 *                              NOT handled here, because there is no message object to hand
 *                              over - it never got built.
 *
 * Case B needs one extra line in each service's application.properties, telling Kafka to use
 * ErrorHandlingDeserializer as a wrapper around the JSON one. Without it, case B still spins
 * forever. See any consumer service's application.properties for the exact two lines.
 *
 * Good news: broken messages skip the retries and go straight to the dead letter topic. Spring
 * knows a malformed message will never fix itself, so retrying it would just waste time.
 *
 * Full write-up: notes/kafka-dead-letter-topics.md
 */
// C1 step 3 - FIX for a startup failure introduced by B2:
//   this class registers two KafkaTemplate beans (the dead-letter ones). Spring Boot creates its
//   OWN default "kafkaTemplate" only if NO KafkaTemplate exists yet. Auto-configs run in
//   alphabetical order by default ("com.project..." comes before "org.springframework..."), so
//   ours ran FIRST, and Boot's default template was never created.
//   Then every producer asking for the default one (KafkaTemplate<String,Object> in
//   ReportingService and Ai-Service) found nothing, and the service could not start.
// "after = KafkaAutoConfiguration" makes Boot create its default template first. Ours are then
// simply extra, alongside it.
@AutoConfiguration(after = KafkaAutoConfiguration.class)
@ConditionalOnClass(KafkaTemplate.class)
public class KafkaErrorHandlingAutoConfig {

    /** How long to wait between retries, in milliseconds. */
    private static final long RETRY_INTERVAL_MS = 1000L;

    /**
     * How many times to retry before giving up and sending to the dead letter topic.
     *
     * Why 3 and not more? Retries only help for problems that fix themselves - a brief database
     * outage, a network hiccup. If three tries a second apart have not worked, waiting longer
     * rarely helps, and every extra retry is more time the whole partition sits blocked.
     */
    private static final long MAX_RETRIES = 3L;

    /**
     * The error handler itself. This replaces Kafka's "retry forever" default.
     *
     * @ConditionalOnMissingBean means a service can define its own error handler if it ever needs
     * different behaviour, and this one steps aside quietly rather than fighting it.
     */
    @Bean
    @ConditionalOnMissingBean(CommonErrorHandler.class)
    public DefaultErrorHandler kafkaErrorHandler(DeadLetterPublishingRecoverer recoverer) {

        DefaultErrorHandler handler = new DefaultErrorHandler(
                recoverer,                                             // what to do when we give up
                new FixedBackOff(RETRY_INTERVAL_MS, MAX_RETRIES));     // how many tries, how far apart

        // Turn the retry attempts down to a quieter log level. Without this, every single retry
        // logs a full stack trace, so one bad message produces four alarming-looking errors. We
        // only really want to shout when we finally give up.
        handler.setLogLevel(KafkaException.Level.WARN);

        return handler;
    }

    /**
     * Decides which dead letter topic a failed message goes to, and sends it there.
     * A message that failed on "incident.created" is republished to "incident.created-dlt".
     * Spring picks that name for us.
     *
     * WHY TWO PRODUCERS INSTEAD OF ONE
     * A serializer turns a value into bytes, and only one runs per message - they do not chain.
     * The failed message can be in one of two shapes, needing two different serializers:
     *
     *   Failed inside the listener  -> we hold a real Java object -> JsonSerializer
     *                                  (converts object to JSON, then to bytes)
     *   Failed during deserializing -> we only hold raw bytes     -> ByteArraySerializer
     *                                  (converts nothing, sends the bytes as they are)
     *
     * The second one is deliberate. The dead letter topic then holds exactly what arrived, so
     * you can read the broken JSON yourself and see what was wrong with it.
     *
     * The map below tells Spring which to use. It MUST be a LinkedHashMap, because the map is
     * checked in order and byte[] has to be tried before the catch-all Object entry.
     */
    @Bean
    @ConditionalOnMissingBean
    public DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(
            KafkaTemplate<Object, Object> dltRawBytesTemplate,
            KafkaTemplate<Object, Object> dltJsonTemplate) {

        Map<Class<?>, KafkaOperations<?, ?>> templates = new LinkedHashMap<>();
        templates.put(byte[].class, dltRawBytesTemplate);   // deserialization failures
        templates.put(Object.class, dltJsonTemplate);       // everything else

        return new DeadLetterPublishingRecoverer(templates);
    }

    /**
     * Producer for messages that failed to deserialize. Sends the original bytes unchanged.
     *
     * We build our own producer here instead of reusing the service's, because a consumer-only
     * service (like NotificationService) has no producer configured at all. Spring Boot's default
     * would then be StringSerializer, which cannot send a Java object. Bringing our own means
     * this works the same way in every service, with nothing to remember per service.
     */
    @Bean
    @ConditionalOnMissingBean(name = "dltRawBytesTemplate")
    public KafkaTemplate<Object, Object> dltRawBytesTemplate(KafkaProperties properties) {
        Map<String, Object> config = new HashMap<>(properties.buildProducerProperties(null));
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(config));
    }

    /**
     * Producer for messages that deserialized fine but then failed inside the listener.
     * Turns the Java object back into JSON.
     */
    @Bean
    @ConditionalOnMissingBean(name = "dltJsonTemplate")
    public KafkaTemplate<Object, Object> dltJsonTemplate(KafkaProperties properties) {
        Map<String, Object> config = new HashMap<>(properties.buildProducerProperties(null));
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(config));
    }
}
