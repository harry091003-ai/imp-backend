package com.project.outbox.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.outbox.OutboxProperties;
import com.project.outbox.OutboxRelay;
import com.project.outbox.OutboxScheduler;
import com.project.outbox.OutboxWriter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.JacksonUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.ClassUtils;

/**
 * Switches the outbox on in any service that has this module on its classpath AND has a database,
 * transactions and Kafka. Nothing to wire by hand.
 *
 * Runs AFTER Spring Boot has made the JdbcTemplate, the transaction manager and the Kafka producer
 * factory, because the conditions below check that those exist. (The same ordering lesson as
 * issue 50.)
 *
 * The table itself is NOT created here: each service adds it with its own Flyway migration
 * (copy of outbox/outbox_event_mysql.sql in this jar).
 */
@AutoConfiguration(
        after = {KafkaAutoConfiguration.class, JdbcTemplateAutoConfiguration.class,
                DataSourceTransactionManagerAutoConfiguration.class},
        afterName = "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration")
@ConditionalOnClass({KafkaTemplate.class, JdbcTemplate.class})
@ConditionalOnProperty(prefix = "outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnBean({JdbcTemplate.class, PlatformTransactionManager.class, ProducerFactory.class})
@EnableConfigurationProperties(OutboxProperties.class)
public class OutboxAutoConfig {

    // Kept private ON PURPOSE, not a @Bean: a public ObjectMapper bean would quietly REPLACE the
    // service's own ObjectMapper (Spring Boot only makes its one if none exists). This is the same
    // JSON setup Kafka's JsonSerializer uses, so dates and ids survive the trip into the table and back.
    private final ObjectMapper mapper = JacksonUtils.enhancedObjectMapper();

    @Bean
    @ConditionalOnMissingBean
    public OutboxWriter outboxWriter(JdbcTemplate jdbc) {
        return new OutboxWriter(jdbc, mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public OutboxRelay outboxRelay(JdbcTemplate jdbc, PlatformTransactionManager transactionManager,
                                   ProducerFactory<?, ?> producerFactory, OutboxProperties props) {
        return new OutboxRelay(jdbc, transactionManager, producerFactory, props, mapper,
                ClassUtils.getDefaultClassLoader());
    }

    @Bean
    @ConditionalOnMissingBean
    public OutboxScheduler outboxScheduler(OutboxRelay relay, OutboxProperties props) {
        return new OutboxScheduler(relay, props);
    }
}
