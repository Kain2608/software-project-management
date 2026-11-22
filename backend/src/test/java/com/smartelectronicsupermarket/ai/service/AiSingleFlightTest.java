package com.smartelectronicsupermarket.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AiSingleFlightTest {

    @Test
    void concurrentCallersWithTheSameKeyShareOneExecution() throws Exception {
        AiSingleFlight<String> flight = new AiSingleFlight<>();
        AtomicInteger executions = new AtomicInteger();
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(20);
        List<Future<String>> results = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            results.add(pool.submit(() -> flight.run("k", () -> {
                executions.incrementAndGet();
                try {
                    release.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "shared";
            }, "failed", 3000)));
        }
        Thread.sleep(300);
        release.countDown();
        for (Future<String> result : results) {
            assertEquals("shared", result.get(3, TimeUnit.SECONDS));
        }
        pool.shutdownNow();
        assertEquals(1, executions.get());
        assertEquals(0, flight.inFlightCount());
    }

    @Test
    void differentKeysRunIndependently() throws Exception {
        AiSingleFlight<String> flight = new AiSingleFlight<>();
        AtomicInteger executions = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<String> a = pool.submit(() -> flight.run("a", () -> { executions.incrementAndGet(); return "A"; }, "x", 500));
        Future<String> b = pool.submit(() -> flight.run("b", () -> { executions.incrementAndGet(); return "B"; }, "x", 500));
        assertEquals("A", a.get());
        assertEquals("B", b.get());
        pool.shutdownNow();
        assertEquals(2, executions.get());
    }

    @Test
    void followerThatWaitsTooLongGetsTheFailureValue() throws Exception {
        AiSingleFlight<String> flight = new AiSingleFlight<>();
        CountDownLatch leaderStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<String> leader = pool.submit(() -> flight.run("k", () -> {
            leaderStarted.countDown();
            try {
                release.await(3, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "late";
        }, "failed", 3000));
        assertTrue(leaderStarted.await(1, TimeUnit.SECONDS));
        assertEquals("timeout-value", flight.run("k", () -> "never", "timeout-value", 100));
        release.countDown();
        assertEquals("late", leader.get());
        pool.shutdownNow();
    }

    @Test
    void leaderFailureIsRethrownAndTheKeyIsReleased() {
        AiSingleFlight<String> flight = new AiSingleFlight<>();
        assertThrows(IllegalStateException.class, () -> flight.run("k", () -> {
            throw new IllegalStateException("boom");
        }, "failed", 100));
        assertEquals(0, flight.inFlightCount());
        assertEquals("ok", flight.run("k", () -> "ok", "failed", 100));
    }
}
