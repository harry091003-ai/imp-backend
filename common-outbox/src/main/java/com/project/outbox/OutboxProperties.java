package com.project.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings, read from application.properties under "outbox.".
 * Every value has a sensible default, so a service needs to set none of them.
 */
@ConfigurationProperties(prefix = "outbox")
public class OutboxProperties {

    /** Turn the whole outbox (writer + relay) on or off. */
    private boolean enabled = true;

    /** How long the relay waits between rounds. */
    private long pollDelayMs = 1_000;

    /** Most rows the relay picks up in one round. Small, so one round is quick. */
    private int batchSize = 50;

    /** How long to wait for Kafka to confirm one message. */
    private long sendTimeoutMs = 10_000;

    /**
     * How long Kafka's own client may wait for a broker that is down before giving up.
     * Its default is 60 seconds, which would freeze the relay for a full minute per round.
     */
    private long maxBlockMs = 5_000;

    /** A row stuck in SENDING longer than this (the app crashed mid-send) goes back to NEW. */
    private long claimTimeoutMs = 120_000;

    /** SENT rows older than this many days are deleted. */
    private int retentionDays = 7;

    /** How often to run that cleanup. */
    private long cleanupEveryMs = 3_600_000;

    /** Log a warning if the oldest unsent event is older than this. Usually means Kafka is down. */
    private long stuckWarnAfterMs = 60_000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getPollDelayMs() {
        return pollDelayMs;
    }

    public void setPollDelayMs(long pollDelayMs) {
        this.pollDelayMs = pollDelayMs;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public long getSendTimeoutMs() {
        return sendTimeoutMs;
    }

    public void setSendTimeoutMs(long sendTimeoutMs) {
        this.sendTimeoutMs = sendTimeoutMs;
    }

    public long getMaxBlockMs() {
        return maxBlockMs;
    }

    public void setMaxBlockMs(long maxBlockMs) {
        this.maxBlockMs = maxBlockMs;
    }

    public long getClaimTimeoutMs() {
        return claimTimeoutMs;
    }

    public void setClaimTimeoutMs(long claimTimeoutMs) {
        this.claimTimeoutMs = claimTimeoutMs;
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }

    public long getCleanupEveryMs() {
        return cleanupEveryMs;
    }

    public void setCleanupEveryMs(long cleanupEveryMs) {
        this.cleanupEveryMs = cleanupEveryMs;
    }

    public long getStuckWarnAfterMs() {
        return stuckWarnAfterMs;
    }

    public void setStuckWarnAfterMs(long stuckWarnAfterMs) {
        this.stuckWarnAfterMs = stuckWarnAfterMs;
    }
}
