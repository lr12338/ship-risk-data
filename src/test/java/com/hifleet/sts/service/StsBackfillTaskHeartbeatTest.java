package com.hifleet.sts.service;

import com.hifleet.sts.repository.StsBackfillTaskRepository;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

public class StsBackfillTaskHeartbeatTest {

    @Test
    public void touchesRunningTaskAndShutsDownOnClose() {
        StsBackfillTaskRepository repository = mock(StsBackfillTaskRepository.class);
        StsBackfillTaskHeartbeat heartbeat = new StsBackfillTaskHeartbeat(repository);

        StsBackfillTaskHeartbeat.Handle handle =
                heartbeat.start(41L, 10L, TimeUnit.MILLISECONDS);
        verify(repository, timeout(1000).atLeastOnce()).touchRunning(41L);
        handle.close();

        assertTrue(handle.isShutdown());
    }

    @Test
    public void heartbeatDbFailureIsCountedWithoutStoppingScheduler() {
        StsBackfillTaskRepository repository = mock(StsBackfillTaskRepository.class);
        doThrow(new RuntimeException("db unavailable")).when(repository).touchRunning(42L);
        StsBackfillTaskHeartbeat heartbeat = new StsBackfillTaskHeartbeat(repository);

        StsBackfillTaskHeartbeat.Handle handle =
                heartbeat.start(42L, 10L, TimeUnit.MILLISECONDS);
        verify(repository, timeout(1000).atLeastOnce()).touchRunning(42L);
        awaitFailure(handle);
        handle.close();

        assertTrue(handle.getFailureCount() > 0);
        assertTrue(handle.isShutdown());
    }

    private void awaitFailure(StsBackfillTaskHeartbeat.Handle handle) {
        long deadline = System.currentTimeMillis() + 1000L;
        while (handle.getFailureCount() == 0 && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(5L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
