package com.project.outbox;

/**
 * The life of one outbox row.
 *
 * <pre>
 *   NEW ──claim──► SENDING ──Kafka says ok──► SENT      (deleted after a few days)
 *    ▲                │
 *    └── Kafka down ──┘          message itself broken ──► FAILED   (a person must look)
 * </pre>
 */
public final class OutboxStatus {

    private OutboxStatus() {
    }

    /** Saved together with the business data, waiting to be sent. */
    public static final String NEW = "NEW";
    /** The relay picked it up and is sending it right now. */
    public static final String SENDING = "SENDING";
    /** Kafka confirmed it. */
    public static final String SENT = "SENT";
    /** The message itself is broken (for example its class no longer exists). Never retried. */
    public static final String FAILED = "FAILED";
}
