package com.project.outbox.jpatest;

import com.project.outbox.OutboxWriter;
import com.project.outbox.TestOrderEvent;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.support.JacksonUtils;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * A tiny app shaped like our services: a JPA entity, a repository, and a service method with
 * jakarta's @Transactional (the one our services use) that saves the entity AND an outbox row.
 */
@SpringBootApplication
// The repository below is a nested interface; Spring Data skips those unless told to look.
@EnableJpaRepositories(considerNestedRepositories = true)
public class JpaTestApp {

    @Bean
    OutboxWriter outboxWriter(JdbcTemplate jdbc) {
        return new OutboxWriter(jdbc, JacksonUtils.enhancedObjectMapper());
    }

    @Entity
    public static class TestOrder {
        @Id
        private UUID id;
        private String name;

        protected TestOrder() {
        }

        public TestOrder(UUID id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    public interface TestOrderRepository extends JpaRepository<TestOrder, UUID> {
    }

    @Service
    public static class OrderService {
        private final TestOrderRepository repository;
        private final OutboxWriter outbox;

        public OrderService(TestOrderRepository repository, OutboxWriter outbox) {
            this.repository = repository;
            this.outbox = outbox;
        }

        /** Like IncidentService.CreateIncident will be: save the data, save the event, maybe fail. */
        @jakarta.transaction.Transactional
        public void placeOrder(UUID id, boolean failAtTheEnd) {
            repository.save(new TestOrder(id, "order"));
            outbox.save("orders", id.toString(), new TestOrderEvent(id, "order", null));
            if (failAtTheEnd) {
                throw new IllegalStateException("something went wrong after both saves");
            }
        }
    }
}
