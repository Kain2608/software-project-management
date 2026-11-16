package com.smartelectronicsupermarket.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.smartelectronicsupermarket.ai.service.AiCircuitBreaker.State;

class AiCircuitBreakerTest {

    private AtomicLong now;
    private AiCircuitBreaker breaker;

    @BeforeEach
    void setUp() {
        now = new AtomicLong(1_000_000L);
        breaker = new AiCircuitBreaker(now::get);
    }

    private void record(int successes, int failures) {
        for (int i = 0; i < successes; i++) {
            assertTrue(breaker.allowRequest());
            breaker.recordSuccess();
        }
        for (int i = 0; i < failures; i++) {
            assertTrue(breaker.allowRequest());
            breaker.recordFailure();
        }
    }

    @Test
    void staysClosedBelowFiftyPercentFailuresInFullWindow() {
        record(11, 9);
        assertEquals(State.CLOSED, breaker.state());
    }

    @Test
    void opensWhenWindowOf20HasAtLeast50PercentFailures() {
        record(10, 10);
        assertEquals(State.OPEN, breaker.state());
        assertFalse(breaker.allowRequest());
    }

    @Test
    void doesNotOpenBeforeWindowIsFull() {
        record(0, 19);
        assertEquals(State.CLOSED, breaker.state());
    }

    @Test
    void halfOpensAfter15SecondsAndPermitsOnlyFiveProbes() {
        record(0, 20);
        assertEquals(State.OPEN, breaker.state());
        now.addAndGet(14_999L);
        assertFalse(breaker.allowRequest());
        now.addAndGet(1L);
        for (int i = 0; i < 5; i++) {
            assertTrue(breaker.allowRequest(), "probe " + i);
        }
        assertEquals(State.HALF_OPEN, breaker.state());
        assertFalse(breaker.allowRequest(), "sixth probe must be rejected");
    }

    @Test
    void closesAfterFiveSuccessfulProbes() {
        record(0, 20);
        now.addAndGet(15_000L);
        for (int i = 0; i < 5; i++) {
            assertTrue(breaker.allowRequest());
            breaker.recordSuccess();
        }
        assertEquals(State.CLOSED, breaker.state());
    }

    @Test
    void anyProbeFailureReopensImmediately() {
        record(0, 20);
        now.addAndGet(15_000L);
        assertTrue(breaker.allowRequest());
        breaker.recordSuccess();
        assertTrue(breaker.allowRequest());
        breaker.recordFailure();
        assertEquals(State.OPEN, breaker.state());
        assertFalse(breaker.allowRequest());
    }

    @Test
    void forceOverridesState() {
        breaker.force(true);
        assertEquals(State.OPEN, breaker.state());
        breaker.force(false);
        assertEquals(State.CLOSED, breaker.state());
        assertTrue(breaker.allowRequest());
    }
}
