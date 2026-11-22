package com.smartelectronicsupermarket.ai.summary;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.smartelectronicsupermarket.ai.service.AiCircuitBreaker;
import com.smartelectronicsupermarket.ai.service.AiProductNotFoundException;
import com.smartelectronicsupermarket.ai.service.AiSingleFlight;
import com.smartelectronicsupermarket.ai.service.AiScopeDeniedException;
import com.smartelectronicsupermarket.security.authorization.PermissionChecker;

/**
 * FR-09 specification summarizer (TSK-03-01, D55 sections 3.2, 3.3, 5.2, 6.3).
 * Retrieval is a single DB read of the product's own catalog row (the RAG corpus); the model may only restate it.
 * Order of degradation: AI (validated) -> RULE_FALLBACK (verbatim spec pairs) -> SPEC_TABLE_ONLY (raw table).
 */
@Service
public class AiSpecSummaryService {

    private static final Logger log = LoggerFactory.getLogger(AiSpecSummaryService.class);
    static final String AI_USE_PERMISSION = "ai.use";
    static final long TIMEOUT_MS = 2500L; // BR-09
    static final long CACHE_TTL_MS = 24L * 60 * 60 * 1000; // D55 section 5.3
    static final String NOTICE_FALLBACK = "Đang hiển thị bảng thông số tiêu chuẩn từ nhà sản xuất";
    static final String NOTICE_INSUFFICIENT =
            "Chưa đủ thông số trong hệ thống để tóm tắt. Vui lòng xem bảng thông số gốc (nếu có).";

    private final SpecSummaryLlm llm;
    private final SpecSummaryDataAccess dataAccess;
    private final PermissionChecker permissionChecker;
    private final AiCircuitBreaker circuitBreaker;
    private final LongSupplier clock;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();
    private final AiSingleFlight<Outcome> singleFlight = new AiSingleFlight<>();
    private final AtomicInteger threadCounter = new AtomicInteger();
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            0, 32, 30L, TimeUnit.SECONDS, new SynchronousQueue<>(), runnable -> {
                Thread thread = new Thread(runnable, "ai-summary-" + threadCounter.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            });

    private record Cached(List<String> highlights, String audience, long expiresAt) {
    }

    @Autowired
    public AiSpecSummaryService(
            SpecSummaryLlm llm, SpecSummaryDataAccess dataAccess, PermissionChecker permissionChecker) {
        this(llm, dataAccess, permissionChecker, new AiCircuitBreaker(), System::currentTimeMillis);
    }

    public AiSpecSummaryService(
            SpecSummaryLlm llm, SpecSummaryDataAccess dataAccess, PermissionChecker permissionChecker,
            AiCircuitBreaker circuitBreaker, LongSupplier clock) {
        this.llm = llm;
        this.dataAccess = dataAccess;
        this.permissionChecker = permissionChecker;
        this.circuitBreaker = circuitBreaker;
        this.clock = clock;
    }

    public SpecSummaryResult summarize(UUID actorId, SpecSummaryRequest request) {
        long start = System.currentTimeMillis();
        if (actorId == null) {
            throw new IllegalArgumentException("actorId is required");
        }
        UUID storeId = parse(request.storeId(), "store_id");
        UUID productId = parse(request.productId(), "product_id");
        if (!permissionChecker.hasPermission(actorId, AI_USE_PERMISSION, storeId)) {
            throw new AiScopeDeniedException("ai.use is not granted for the requested store");
        }
        SpecProduct product = dataAccess.findProduct(productId)
                .filter(SpecProduct::active)
                .orElseThrow(() -> new AiProductNotFoundException("product not found or inactive"));

        Map<String, String> specs = SpecSanitizer.sanitize(product.specificationsJson());
        if (specs.isEmpty()) {
            return done(start, product, specs, SpecSummaryResult.STATUS_INSUFFICIENT_DATA, "RULE_FALLBACK",
                    List.of(), null, NOTICE_INSUFFICIENT, "EMPTY_SPECS");
        }

        String key = "ai:summary:v1:" + SpecSanitizer.fingerprint(product.specificationsJson()) + ":"
                + llm.modelVersion() + ":" + product.id() + ":" + request.locale();
        Cached hit = cache.get(key);
        if (hit != null && hit.expiresAt() > clock.getAsLong()) {
            return done(start, product, specs, SpecSummaryResult.STATUS_SUCCESS, "CACHE",
                    hit.highlights(), hit.audience(), null, "NONE");
        }
        cache.remove(key);

        // Concurrent misses for the same product+specs share one model call (single-flight).
        Outcome outcome = singleFlight.run(key, () -> lead(key, product, specs),
                new Outcome(null, "LLM_TIMEOUT_EXCEEDED_2500MS", false), TIMEOUT_MS + 500);
        if (outcome.result() != null) {
            return done(start, product, specs, SpecSummaryResult.STATUS_SUCCESS, outcome.fromCache() ? "CACHE" : "AI",
                    outcome.result().highlights(), outcome.result().targetAudience(), null,
                    outcome.result().rejected().isEmpty() ? "NONE"
                            : "PARTIAL_REJECTED_" + outcome.result().rejected().size());
        }
        String reason = outcome.reason();
        List<String> rule = SpecRuleSummarizer.highlights(specs);
        if (!rule.isEmpty()) {
            return done(start, product, specs, SpecSummaryResult.STATUS_SUCCESS, "RULE_FALLBACK", rule, null,
                    NOTICE_FALLBACK, reason);
        }
        return done(start, product, specs, SpecSummaryResult.STATUS_SPEC_TABLE_ONLY, "RULE_FALLBACK", List.of(),
                null, NOTICE_FALLBACK, reason);
    }

    private record Outcome(SpecSummaryValidator.Result result, String reason, boolean fromCache) {
        Outcome(SpecSummaryValidator.Result result, String reason) {
            this(result, reason, false);
        }
    }

    /** Runs once per key at a time: re-check the cache, ask the breaker, call the model, cache a validated result. */
    private Outcome lead(String key, SpecProduct product, Map<String, String> specs) {
        Cached hit = cache.get(key);
        if (hit != null && hit.expiresAt() > clock.getAsLong()) {
            return new Outcome(new SpecSummaryValidator.Result(hit.highlights(), hit.audience(), List.of()), "NONE", true);
        }
        if (!circuitBreaker.allowRequest()) {
            return new Outcome(null, "CIRCUIT_OPEN");
        }
        Outcome outcome = callLlm(product, specs);
        if (outcome.result() != null) {
            cache.put(key, new Cached(outcome.result().highlights(), outcome.result().targetAudience(),
                    clock.getAsLong() + CACHE_TTL_MS));
        }
        return outcome;
    }

    private Outcome callLlm(SpecProduct product, Map<String, String> specs) {
        String system = SpecSummaryPrompts.SYSTEM;
        String user = SpecSummaryPrompts.user(product, specs);
        Future<String> future = null;
        try {
            future = executor.submit(() -> llm.complete(system, user));
            String raw = future.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            circuitBreaker.recordSuccess();
            SpecSummaryValidator.Result validated = SpecSummaryValidator.validate(raw, specs);
            return validated == null ? new Outcome(null, "LLM_OUTPUT_REJECTED") : new Outcome(validated, "NONE");
        } catch (TimeoutException e) {
            future.cancel(true);
            circuitBreaker.recordFailure();
            return new Outcome(null, "LLM_TIMEOUT_EXCEEDED_2500MS");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            circuitBreaker.recordFailure();
            return new Outcome(null, "LLM_INTERRUPTED");
        } catch (ExecutionException e) {
            circuitBreaker.recordFailure();
            return new Outcome(null, "LLM_ERROR");
        } catch (RejectedExecutionException e) {
            circuitBreaker.recordFailure();
            return new Outcome(null, "LLM_BULKHEAD_FULL");
        }
    }

    private SpecSummaryResult done(
            long start, SpecProduct product, Map<String, String> specs, String status, String source,
            List<String> highlights, String audience, String notice, String reason) {
        long took = System.currentTimeMillis() - start;
        // D55 section 8 audit line: no prompt, no spec text, no user data.
        log.info("[AI-AUDIT] feature=spec_summary source={} status={} latency_ms={} highlights={} fallback_reason={} "
                        + "provider={} breaker={}",
                source, status, took, highlights.size(), reason, llm.providerName(), circuitBreaker.state());
        return new SpecSummaryResult(status, source, product.id(), product.name(), highlights, audience, specs,
                notice, SpecSummaryResult.DISCLAIMER, took);
    }

    private static UUID parse(String value, String field) {
        try {
            return UUID.fromString(value.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(field + " must be a UUID");
        }
    }
}
