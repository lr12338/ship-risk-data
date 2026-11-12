package com.hifleet.worldsea;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertEquals;

public class WorldSeaMaskServiceTest {
    @Test
    public void concurrentFirstLoadHappensOnlyOnce() throws Exception {
        final AtomicInteger loads = new AtomicInteger();
        final WorldSeaMaskSnapshot snapshot = WorldSeaTestFixtures.snapshot();
        WorldSeaMaskLoader loader = new WorldSeaMaskLoader(null, new WorldSeaMaskProperties()) {
            @Override public WorldSeaMaskSnapshot load() { loads.incrementAndGet(); return snapshot; }
        };
        final WorldSeaMaskService service = new WorldSeaMaskService(loader);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<WorldSeaMaskSnapshot>> futures = new ArrayList<Future<WorldSeaMaskSnapshot>>();
        for (int i = 0; i < 30; i++) futures.add(pool.submit(new Callable<WorldSeaMaskSnapshot>() {
            @Override public WorldSeaMaskSnapshot call() { return service.requireSnapshot(); }
        }));
        for (Future<WorldSeaMaskSnapshot> future : futures) assertSame(snapshot, future.get());
        pool.shutdown();
        assertEquals(1, loads.get());
    }

    @Test(expected = IllegalStateException.class)
    public void loadFailureIsFailClosed() {
        new WorldSeaMaskService(new WorldSeaMaskLoader(null, new WorldSeaMaskProperties()) {
            @Override public WorldSeaMaskSnapshot load() { throw new IllegalStateException("database down"); }
        }).requireSnapshot();
    }
}
