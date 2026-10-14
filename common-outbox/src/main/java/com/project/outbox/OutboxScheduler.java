package com.project.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Runs the relay every pollDelayMs, on its OWN single thread called "outbox-relay".
 *
 * Why its own thread and not @Scheduled: @Scheduled shares one thread with the service's other
 * scheduled jobs (ReportingService has three). If Kafka is down, a relay round can take a few
 * seconds and would delay those jobs. It also means the module does not switch on scheduling
 * for a whole service just by being on the classpath.
 *
 * One thread = one round at a time = events leave in the order they were saved.
 */
public class OutboxScheduler implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(OutboxScheduler.class);

    /** After an outage, send up to this many full batches in one go to catch up. */
    private static final int MAX_BATCHES_PER_ROUND = 10;

    private static final long STUCK_CHECK_EVERY_MS = 60_000;

    private final OutboxRelay relay;
    private final OutboxProperties props;

    private ScheduledExecutorService executor;
    private volatile boolean running;
    private long lastCleanup;
    private long lastStuckCheck;

    public OutboxScheduler(OutboxRelay relay, OutboxProperties props) {
        this.relay = relay;
        this.props = props;
    }

    @Override
    public void start() {
//        It’s a thread with its own timer , which we named outbox-relay.
        executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "outbox-relay");
            thread.setDaemon(true);
            return thread;
        });
        //tells to execute the round method after pollDelayMs time and then repeat every pollDelayMs time.....
        // which function to run, first waiting time, wait between every runs,units
        executor.scheduleWithFixedDelay(this::round, props.getPollDelayMs(), props.getPollDelayMs(),
                TimeUnit.MILLISECONDS);
        running = true;
        log.info("Outbox relay started (every {} ms, up to {} events per batch)",
                props.getPollDelayMs(), props.getBatchSize());
    }
    //this help in shutting down the thread gracefully and also closing the relay connection to kafka
    @Override
    public void stop() {
        running = false;
        if (executor != null) {
            executor.shutdown();
            try {//if the thread is in  running state then wait for 10 seconds to complete the task and then shutdown the thread
                executor.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        // closing the relay connection to kafka
        relay.close();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /** One round. Package-private so tests can run it without waiting. */
    void round() {
        // Nothing may escape this method: a ScheduledExecutorService silently STOPS repeating
        // a task that throws once. One bad round would end the relay for good.
        try {
            relay.releaseStuckRows();

            int batches = 0;
            while (relay.publishBatch() == props.getBatchSize() && ++batches < MAX_BATCHES_PER_ROUND) {
                // a full batch means more may be waiting: keep going (bounded)
            }

            long now = System.currentTimeMillis();
            if (now - lastStuckCheck >= STUCK_CHECK_EVERY_MS) {
                lastStuckCheck = now;
                relay.warnIfStuck();
            }
            if (now - lastCleanup >= props.getCleanupEveryMs()) {
                lastCleanup = now;
                int deleted = relay.deleteOldSentRows();
                if (deleted > 0) {
                    log.info("Outbox: deleted {} sent event(s) older than {} days", deleted, props.getRetentionDays());
                }
            }
        } catch (Throwable t) {
            log.error("Outbox relay round failed; trying again in {} ms", props.getPollDelayMs(), t);
        }
    }
}
