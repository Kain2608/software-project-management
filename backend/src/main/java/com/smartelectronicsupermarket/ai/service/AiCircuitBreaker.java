package com.smartelectronicsupermarket.ai.service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.LongSupplier;

/**
 * Circuit breaker per D55 section 7: sliding window of 20 calls, opens at >= 50% failures,
 * stays open 15 s, then half-open with 5 probe calls. In half-open any failure re-opens it
 * and 5 successful probes close it.
 */
public class AiCircuitBreaker {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    static final int WINDOW_SIZE = 20;
    static final double FAILURE_RATE_THRESHOLD = 0.5;
    static final long OPEN_DURATION_MS = 15_000L;
    static final int HALF_OPEN_PERMITTED_CALLS = 5;

    private final LongSupplier clockMillis;
    private final Deque<Boolean> window = new ArrayDeque<>(); // true = failure
    private State state = State.CLOSED;
    private long openedAt;
    private int halfOpenPermitted;
    private int halfOpenSucceeded;

    public AiCircuitBreaker() {
        this(System::currentTimeMillis);
    }

    public AiCircuitBreaker(LongSupplier clockMillis) {
        this.clockMillis = clockMillis;
    }

    /** True when the LLM may be called; false means go straight to the rule fallback. */
    public synchronized boolean allowRequest() {
        if (state == State.OPEN && clockMillis.getAsLong() - openedAt >= OPEN_DURATION_MS) {
            state = State.HALF_OPEN;
            halfOpenPermitted = 0;
            halfOpenSucceeded = 0;
        }
        return switch (state) {
            case CLOSED -> true;
            case OPEN -> false;
            case HALF_OPEN -> {
                if (halfOpenPermitted < HALF_OPEN_PERMITTED_CALLS) {
                    halfOpenPermitted++;
                    yield true;
                }
                yield false;
            }
        };
    }

    public synchronized void recordSuccess() {
        if (state == State.HALF_OPEN) {
            halfOpenSucceeded++;
            if (halfOpenSucceeded >= HALF_OPEN_PERMITTED_CALLS) {
                state = State.CLOSED;
                window.clear();
            }
        } else if (state == State.CLOSED) {
            push(false);
        }
    }

    public synchronized void recordFailure() {
        if (state == State.HALF_OPEN) {
            open();
        } else if (state == State.CLOSED) {
            push(true);
            if (window.size() >= WINDOW_SIZE) {
                long failures = window.stream().filter(Boolean::booleanValue).count();
                if ((double) failures / window.size() >= FAILURE_RATE_THRESHOLD) {
                    open();
                }
            }
        }
    }

    /** Operator override (used by tests and maintenance): force OPEN or reset to CLOSED. */
    public synchronized void force(boolean open) {
        if (open) {
            open();
        } else {
            state = State.CLOSED;
            window.clear();
        }
    }

    public synchronized State state() {
        return state;
    }

    private void push(boolean failure) {
        window.addLast(failure);
        while (window.size() > WINDOW_SIZE) {
            window.removeFirst();
        }
    }

    private void open() {
        state = State.OPEN;
        openedAt = clockMillis.getAsLong();
        window.clear();
    }
}
