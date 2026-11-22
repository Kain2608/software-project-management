package com.smartelectronicsupermarket.ai.service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Request coalescing for expensive model calls: concurrent callers with the same key share one execution
 * (the "leader"); the others wait for the leader's result for at most {@code waitMs}. This prevents a cold-cache
 * stampede from exhausting the LLM bulkhead and tripping the circuit breaker.
 */
public final class AiSingleFlight<T> {

    private final ConcurrentHashMap<String, CompletableFuture<T>> inFlight = new ConcurrentHashMap<>();

    /**
     * @param leaderWork work run by the first caller for this key; it must not depend on the followers
     * @param onWaitFailure value returned to a follower whose wait timed out or whose leader failed
     */
    public T run(String key, Supplier<T> leaderWork, T onWaitFailure, long waitMs) {
        CompletableFuture<T> mine = new CompletableFuture<>();
        CompletableFuture<T> existing = inFlight.putIfAbsent(key, mine);
        if (existing == null) {
            try {
                T value = leaderWork.get();
                mine.complete(value);
                return value;
            } catch (RuntimeException e) {
                mine.completeExceptionally(e);
                throw e;
            } finally {
                inFlight.remove(key, mine);
            }
        }
        try {
            return existing.get(waitMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return onWaitFailure;
        } catch (ExecutionException | TimeoutException e) {
            return onWaitFailure;
        }
    }

    int inFlightCount() {
        return inFlight.size();
    }
}
