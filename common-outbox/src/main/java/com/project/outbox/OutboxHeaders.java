package com.project.outbox;

/** Kafka headers the relay adds to every message it sends. */
public final class OutboxHeaders {

    private OutboxHeaders() {
    }

    /**
     * The outbox row's own id. Unique per event, and the same if the relay ever sends the event twice.
     * A consumer can use it to skip a repeat. (7 of our 13 event classes have no eventId field.)
     */
    public static final String EVENT_ID = "X-Event-Id";
}
