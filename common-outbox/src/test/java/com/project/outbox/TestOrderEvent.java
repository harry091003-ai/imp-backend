package com.project.outbox;

import java.time.LocalDateTime;
import java.util.UUID;

/** A sample event, shaped like ours: an id, some text, a date. Needs a no-arg constructor + setters. */
public class TestOrderEvent {

    private UUID orderId;
    private String name;
    private LocalDateTime createdAt;

    public TestOrderEvent() {
    }

    public TestOrderEvent(UUID orderId, String name, LocalDateTime createdAt) {
        this.orderId = orderId;
        this.name = name;
        this.createdAt = createdAt;
    }

    public static TestOrderEvent named(String name) {
        return new TestOrderEvent(UUID.randomUUID(), name, LocalDateTime.of(2026, 10, 12, 9, 30, 15, 123_000_000));
    }

    public UUID getOrderId() {
        return orderId;
    }

    public void setOrderId(UUID orderId) {
        this.orderId = orderId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
