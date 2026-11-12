package com.hifleet.sts.service;

import com.hifleet.sts.repository.StsBackfillTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class StsBackfillTaskHeartbeat {
    private static final Logger log = LoggerFactory.getLogger(StsBackfillTaskHeartbeat.class);
    private final StsBackfillTaskRepository taskRepository;

    @Value("${ship.behavior.sts.backfill.heartbeat-minutes:5}")
    private long heartbeatMinutes = 5L;

    @Autowired
    public StsBackfillTaskHeartbeat(StsBackfillTaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    public Handle start(Long taskId) {
        return start(taskId, heartbeatMinutes, TimeUnit.MINUTES);
    }

    Handle start(final Long taskId, long interval, TimeUnit unit) {
        final AtomicInteger failures = new AtomicInteger();
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(
                new ThreadFactory() {
                    @Override
                    public Thread newThread(Runnable runnable) {
                        Thread thread = new Thread(runnable, "sts-backfill-heartbeat-" + taskId);
                        thread.setDaemon(true);
                        return thread;
                    }
                });
        executor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    int touched = taskRepository.touchRunning(taskId);
                    if (touched == 0) {
                        log.warn("STS_FLEET_BACKFILL heartbeat did not touch RUNNING taskId={}", taskId);
                    }
                } catch (Exception ex) {
                    int count = failures.incrementAndGet();
                    log.warn("STS_FLEET_BACKFILL heartbeat failed: taskId={}, failures={}, error={}",
                            taskId, count, ex.getMessage());
                }
            }
        }, interval, interval, unit);
        return new Handle(executor, failures);
    }

    public static final class Handle implements AutoCloseable {
        private final ScheduledExecutorService executor;
        private final AtomicInteger failures;

        private Handle(ScheduledExecutorService executor, AtomicInteger failures) {
            this.executor = executor;
            this.failures = failures;
        }

        public int getFailureCount() { return failures.get(); }
        public boolean isShutdown() { return executor.isShutdown(); }
        @Override public void close() { executor.shutdownNow(); }
    }
}
