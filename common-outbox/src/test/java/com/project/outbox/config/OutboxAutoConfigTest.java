package com.project.outbox.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.outbox.OutboxProperties;
import com.project.outbox.OutboxRelay;
import com.project.outbox.OutboxScheduler;
import com.project.outbox.OutboxWriter;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** When does the module switch itself on, and what does it create? */
class OutboxAutoConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OutboxAutoConfig.class))
            .withPropertyValues("outbox.poll-delay-ms=600000");   // no relay round during these tests

    /** What every target service has: a database, transactions and Kafka. */
    @Configuration(proxyBeanMethods = false)
    static class ServiceLikeSetup {
        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:auto-" + UUID.randomUUID() + ";MODE=MySQL", "sa", "");
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        ProducerFactory<Object, Object> kafkaProducerFactory() {
            return new DefaultKafkaProducerFactory<>(Map.of(
                    ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:1",
                    ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                    ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class));
        }
    }

    /** Kafka, but no database (like the gateway would be). */
    @Configuration(proxyBeanMethods = false)
    static class KafkaOnly {
        @Bean
        ProducerFactory<Object, Object> kafkaProducerFactory() {
            return new DefaultKafkaProducerFactory<>(Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:1"));
        }
    }

    @Test
    void inAServiceWithDatabaseTransactionsAndKafkaItSwitchesOn() {
        runner.withUserConfiguration(ServiceLikeSetup.class).run(context -> {
            assertThat(context).hasSingleBean(OutboxWriter.class);
            assertThat(context).hasSingleBean(OutboxRelay.class);
            assertThat(context).hasSingleBean(OutboxScheduler.class);
        });
    }

    @Test
    void itCanBeSwitchedOff() {
        runner.withUserConfiguration(ServiceLikeSetup.class)
                .withPropertyValues("outbox.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(OutboxWriter.class)
                        .doesNotHaveBean(OutboxRelay.class));
    }

    @Test
    void withoutADatabaseItStaysOff() {
        runner.withUserConfiguration(KafkaOnly.class)
                .run(context -> assertThat(context).doesNotHaveBean(OutboxWriter.class));
    }

    @Test
    void itReadsItsSettings() {
        runner.withUserConfiguration(ServiceLikeSetup.class)
                .withPropertyValues("outbox.batch-size=7", "outbox.retention-days=3")
                .run(context -> {
                    OutboxProperties props = context.getBean(OutboxProperties.class);
                    assertThat(props.getBatchSize()).isEqualTo(7);
                    assertThat(props.getRetentionDays()).isEqualTo(3);
                });
    }

    @Test
    void itDoesNotAddAnObjectMapperBean() {
        // A public ObjectMapper bean would replace the service's own one. It must stay private.
        runner.withUserConfiguration(ServiceLikeSetup.class)
                .run(context -> assertThat(context).doesNotHaveBean(ObjectMapper.class));
    }
}
