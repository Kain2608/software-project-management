package com.smartelectronicsupermarket.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import com.smartelectronicsupermarket.ai.adapter.GeminiAdapter;
import com.smartelectronicsupermarket.ai.adapter.OpenAiAdapter;
import com.smartelectronicsupermarket.ai.data.AiCrossSellDataAccess;
import com.smartelectronicsupermarket.ai.data.AiCrossSellDataAccess.VerifiedProduct;
import com.smartelectronicsupermarket.ai.dto.CrossSellCandidate;
import com.smartelectronicsupermarket.ai.dto.CrossSellRequest;
import com.smartelectronicsupermarket.ai.dto.CrossSellResult;
import com.smartelectronicsupermarket.ai.service.AiCacheManager;
import com.smartelectronicsupermarket.ai.service.AiCircuitBreaker;
import com.smartelectronicsupermarket.ai.service.AiCrossSellService;
import com.smartelectronicsupermarket.ai.service.AiRuleFallbackEngine;
import com.smartelectronicsupermarket.ai.service.AiScopeDeniedException;
import com.smartelectronicsupermarket.ai.service.AiSkuNotFoundException;
import com.smartelectronicsupermarket.security.authorization.PermissionChecker;

/**
 * Produces the Sprint 2 (S2-08 / DS2) evidence files by really running {@link AiCrossSellService}.
 * Disabled unless SASMS_AI_EVIDENCE_DIR is set. The LLM is a latency/fault-injecting adapter and the
 * database is an in-memory stub: this measures the engine (cache, timeout, breaker, fallback, verification),
 * not a remote LLM provider or PostgreSQL.
 */
class AiSprint2EvidenceTest {

    private static final UUID ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000811");
    private static final UUID STORE = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID WAREHOUSE = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID OTHER_STORE_WH = UUID.fromString("00000000-0000-0000-0000-000000000999");
    private static final Set<String> CART_SKUS = Set.of(
            "PHONE-A", "PHONE-B", "PHONE-C", "PHONE-D", "PHONE-E", "PHONE-HANG-01", "PHONE-HANG-02", "PHONE-HANG-03", "PHONE-HANG-04",
            "PHONE-HANG-05", "PHONE-HANG-06", "PHONE-HANG-07", "PHONE-HANG-08", "PHONE-HANG-09", "PHONE-HANG-10", "PHONE-ERR-1", "OOS-1", "PHONE-OOS");

    /** LLM stand-in: 700-1400 ms normally, hangs 5 s for *HANG-*, throws for *ERR-*. */
    private static final java.util.concurrent.atomic.AtomicInteger LLM_CALLS = new java.util.concurrent.atomic.AtomicInteger();

    private static class FaultInjectingLlm extends OpenAiAdapter {
        @Override
        public List<CrossSellCandidate> generateCrossSell(List<String> skuIds, String storeId) {
            LLM_CALLS.incrementAndGet();
            String sku = skuIds.get(0);
            try {
                if (sku.startsWith("PHONE-HANG-")) {
                    Thread.sleep(5_000);
                } else {
                    Thread.sleep(700 + Math.floorMod(sku.hashCode(), 700));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (sku.contains("ERR-")) {
                throw new IllegalStateException("simulated provider 503");
            }
            if (sku.contains("OOS")) {
                return List.of(new CrossSellCandidate("ACC-OUT-OF-STOCK", "simulated suggestion with no stock"));
            }
            return List.of(
                    new CrossSellCandidate("ACC-CASE-IP16", "Ốp lưng chống sốc tương thích " + sku),
                    new CrossSellCandidate("ACC-CHG-20W", "Củ sạc nhanh Type-C 20W"));
        }
    }

    private static VerifiedProduct product(String sku, String price, long qty) {
        return new VerifiedProduct(UUID.nameUUIDFromBytes(sku.getBytes(StandardCharsets.UTF_8)), sku,
                "Sản phẩm " + sku, new BigDecimal(price), qty, "Phụ kiện");
    }

    private static AiCrossSellDataAccess stubDatabase() {
        AiCrossSellDataAccess db = mock(AiCrossSellDataAccess.class);
        Map<String, VerifiedProduct> stock = Map.of(
                "ACC-CASE-IP16", product("ACC-CASE-IP16", "450000.00", 15),
                "ACC-CHG-20W", product("ACC-CHG-20W", "590000.00", 28),
                "ACC-DEF-CASE", product("ACC-DEF-CASE", "250000.00", 20),
                "ACC-DEF-CBL", product("ACC-DEF-CBL", "180000.00", 35),
                "SRV-WAR-12M", product("SRV-WAR-12M", "800000.00", 9));
        when(db.warehouseBelongsToStore(WAREHOUSE, STORE)).thenReturn(true);
        when(db.warehouseBelongsToStore(OTHER_STORE_WH, STORE)).thenReturn(false);
        when(db.findProductIdsBySku(anyCollection())).thenAnswer(invocation -> {
            Collection<String> requested = invocation.getArgument(0);
            Map<String, UUID> found = new TreeMap<>();
            for (String sku : requested) {
                if (CART_SKUS.contains(sku)) {
                    found.put(sku, UUID.nameUUIDFromBytes(sku.getBytes(StandardCharsets.UTF_8)));
                }
            }
            return found;
        });
        when(db.findSellableProducts(anyCollection(), eq(WAREHOUSE))).thenAnswer(invocation -> {
            Collection<String> requested = invocation.getArgument(0);
            List<VerifiedProduct> found = new ArrayList<>();
            for (String sku : requested) {
                if (stock.containsKey(sku)) {
                    found.add(stock.get(sku));
                }
            }
            return found;
        });
        return db;
    }

    private static AiCrossSellService newService(PermissionChecker permissions, AiCircuitBreaker breaker) {
        return new AiCrossSellService(new FaultInjectingLlm(), new GeminiAdapter(), new AiRuleFallbackEngine(),
                new AiCacheManager(), permissions, stubDatabase(), breaker);
    }

    private static PermissionChecker allowAll() {
        PermissionChecker permissions = mock(PermissionChecker.class);
        when(permissions.hasPermission(any(), eq("ai.use"), eq(STORE))).thenReturn(true);
        return permissions;
    }

    private static CrossSellRequest request(String sku, UUID warehouse) {
        return new CrossSellRequest(STORE.toString(), warehouse.toString(), null, List.of(sku), 4);
    }

    private static long percentile(List<Long> sortedMs, double p) {
        int rank = (int) Math.ceil(p / 100.0 * sortedMs.size());
        return sortedMs.get(Math.max(0, rank - 1));
    }

    private static String json(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    @Test
    void generateSprint2Evidence() throws Exception {
        String dir = System.getenv("SASMS_AI_EVIDENCE_DIR");
        assumeTrue(dir != null && !dir.isBlank(), "set SASMS_AI_EVIDENCE_DIR to generate evidence");
        Path out = Paths.get(dir);
        Files.createDirectories(out);

        Logger serviceLogger = (Logger) LoggerFactory.getLogger(AiCrossSellService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        serviceLogger.addAppender(appender);
        List<String> sourceLog = new ArrayList<>();
        sourceLog.add("# SASMS Sprint 2 S2-08 - AI engine source log (real execution of AiCrossSellService)");
        sourceLog.add("# Generated: " + Instant.now());
        sourceLog.add("# LLM = latency/fault injecting adapter; DB = in-memory stub; no remote provider or PostgreSQL.");

        // ---------------- Part A: demo scenarios (sequential, deterministic) ----------------
        List<String> scenarios = new ArrayList<>();
        AiCircuitBreaker demoBreaker = new AiCircuitBreaker();
        AiCrossSellService demo = newService(allowAll(), demoBreaker);
        int[] passed = {0};

        record Step(String id, String name, String expected, Callable<String> action) {
        }
        List<Step> steps = new ArrayList<>();
        steps.add(new Step("DEMO-01", "Goi y moi qua LLM (cache miss)", "AI",
                () -> demo.getCrossSellRecommendations(ACTOR, request("PHONE-A", WAREHOUSE)).source()));
        steps.add(new Step("DEMO-02", "Lan 2 cung gio hang -> cache hit", "CACHE",
                () -> demo.getCrossSellRecommendations(ACTOR, request("PHONE-A", WAREHOUSE)).source()));
        steps.add(new Step("DEMO-03", "LLM treo > 2.5s -> luat du phong", "RULE_FALLBACK",
                () -> demo.getCrossSellRecommendations(ACTOR, request("PHONE-HANG-01", WAREHOUSE)).source()));
        steps.add(new Step("DEMO-04", "LLM loi 503 -> luat du phong", "RULE_FALLBACK",
                () -> demo.getCrossSellRecommendations(ACTOR, request("PHONE-ERR-1", WAREHOUSE)).source()));
        steps.add(new Step("DEMO-05", "Goi y LLM het hang -> luat du phong lay tu DB", "RULE_FALLBACK",
                () -> demo.getCrossSellRecommendations(ACTOR, request("PHONE-OOS", WAREHOUSE)).source()));
        steps.add(new Step("DEMO-05B", "Het hang va khong co luat phu hop -> 200 danh sach rong", "AI:0",
                () -> {
                    CrossSellResult empty = demo.getCrossSellRecommendations(ACTOR, request("OOS-1", WAREHOUSE));
                    return empty.source() + ":" + empty.suggestions().size();
                }));
        steps.add(new Step("DEMO-06", "Circuit breaker OPEN -> luat du phong ngay", "RULE_FALLBACK",
                () -> {
                    demo.setCircuitBreakerOpen(true);
                    String source = demo.getCrossSellRecommendations(ACTOR, request("PHONE-B", WAREHOUSE)).source();
                    demo.setCircuitBreakerOpen(false);
                    return source;
                }));
        steps.add(new Step("DEMO-07", "Thu ngan khong co ai.use tai store -> 403", "FORBIDDEN_SCOPE", () -> {
            PermissionChecker none = mock(PermissionChecker.class);
            try {
                newService(none, new AiCircuitBreaker()).getCrossSellRecommendations(ACTOR,
                        request("PHONE-A", WAREHOUSE));
                return "NO_ERROR";
            } catch (AiScopeDeniedException e) {
                return "FORBIDDEN_SCOPE";
            }
        }));
        steps.add(new Step("DEMO-08", "Kho khong thuoc store -> 403", "FORBIDDEN_SCOPE", () -> {
            try {
                demo.getCrossSellRecommendations(ACTOR, request("PHONE-A", OTHER_STORE_WH));
                return "NO_ERROR";
            } catch (AiScopeDeniedException e) {
                return "FORBIDDEN_SCOPE";
            }
        }));
        steps.add(new Step("DEMO-09", "SKU khong ton tai trong CSDL -> 422", "SKU_NOT_FOUND", () -> {
            try {
                demo.getCrossSellRecommendations(ACTOR, request("UNKNOWN-SKU", WAREHOUSE));
                return "NO_ERROR";
            } catch (AiSkuNotFoundException e) {
                return "SKU_NOT_FOUND";
            }
        }));

        for (Step step : steps) {
            long start = System.nanoTime();
            String actual = step.action().call();
            double ms = (System.nanoTime() - start) / 1_000_000.0;
            boolean ok = step.expected().equals(actual);
            if (ok) {
                passed[0]++;
            }
            scenarios.add("    {\"id\": " + json(step.id()) + ", \"name\": " + json(step.name())
                    + ", \"expected\": " + json(step.expected()) + ", \"actual\": " + json(actual)
                    + ", \"latency_ms\": " + String.format(java.util.Locale.ROOT, "%.2f", ms)
                    + ", \"result\": " + json(ok ? "PASS" : "FAIL") + "}");
            assertEquals(step.expected(), actual, step.id());
        }
        sourceLog.add("");
        sourceLog.add("## Part A - demo scenarios (" + passed[0] + "/" + steps.size() + " PASS)");
        for (ILoggingEvent event : new ArrayList<>(appender.list)) {
            if (event.getFormattedMessage().startsWith("[AI-AUDIT]")) {
                sourceLog.add(event.getFormattedMessage());
            }
        }
        appender.list.clear();

        // ---------------- Part B: load benchmark, 100 requests / 50 concurrent ----------------
        AiCrossSellService loaded = newService(allowAll(), new AiCircuitBreaker());
        List<String> workload = new ArrayList<>();
        String[] normal = {"PHONE-A", "PHONE-B", "PHONE-C", "PHONE-D", "PHONE-E"};
        for (int i = 0; i < 90; i++) {
            workload.add(normal[i % normal.length]);
        }
        for (int i = 1; i <= 10; i++) {
            workload.add(String.format("PHONE-HANG-%02d", i));
        }
        Collections.shuffle(workload, new Random(20261024L));

        LLM_CALLS.set(0);
        ExecutorService pool = Executors.newFixedThreadPool(50);
        List<Future<Object[]>> futures = new ArrayList<>();
        long wallStart = System.nanoTime();
        for (String sku : workload) {
            futures.add(pool.submit(() -> {
                long start = System.nanoTime();
                CrossSellResult result = loaded.getCrossSellRecommendations(ACTOR, request(sku, WAREHOUSE));
                long ms = Math.round((System.nanoTime() - start) / 1_000_000.0);
                return new Object[] {result.source(), ms, result.suggestions().size()};
            }));
        }
        List<Long> latencies = new ArrayList<>();
        Map<String, Integer> sourceCount = new TreeMap<>();
        int emptyResponses = 0;
        for (Future<Object[]> future : futures) {
            Object[] row = future.get();
            latencies.add((Long) row[1]);
            sourceCount.merge((String) row[0], 1, Integer::sum);
            if ((Integer) row[2] == 0) {
                emptyResponses++;
            }
        }
        double wallSeconds = (System.nanoTime() - wallStart) / 1_000_000_000.0;
        int llmCalls = LLM_CALLS.get();
        pool.shutdown();
        Collections.sort(latencies);
        double avg = latencies.stream().mapToLong(Long::longValue).average().orElse(0);

        sourceLog.add("");
        sourceLog.add("## Part B - benchmark 100 requests / 50 concurrent workers (arrival order)");
        Map<String, Long> reasons = new TreeMap<>();
        for (ILoggingEvent event : new ArrayList<>(appender.list)) {
            String message = event.getFormattedMessage();
            if (message.startsWith("[AI-AUDIT]")) {
                sourceLog.add(message);
                String reason = message.replaceAll(".*fallback_reason=(\\S+).*", "$1");
                reasons.merge(reason, 1L, Long::sum);
            }
        }
        serviceLogger.detachAppender(appender);
        Files.write(out.resolve("ds2-ai-source-log.log"), sourceLog, StandardCharsets.UTF_8);

        long p95 = percentile(latencies, 95);
        StringBuilder bench = new StringBuilder();
        bench.append("{\n");
        bench.append("  \"task\": \"S2-08 TSK-02-07\",\n");
        bench.append("  \"generated_at\": ").append(json(Instant.now().toString())).append(",\n");
        bench.append("  \"method\": \"JUnit AiSprint2EvidenceTest: real AiCrossSellService, fault-injecting LLM adapter, in-memory DB stub (no remote LLM, no PostgreSQL)\",\n");
        bench.append("  \"requests\": ").append(latencies.size()).append(",\n");
        bench.append("  \"concurrency\": 50,\n");
        bench.append("  \"workload\": \"90 requests on 5 cart SKUs (LLM 700-1400 ms), 10 requests on 10 distinct SKUs whose LLM hangs 5 s\",\n");
        bench.append("  \"llm_calls_executed\": ").append(llmCalls).append(",\n");
        bench.append("  \"distinct_cache_keys\": 15,\n");
        bench.append("  \"wall_clock_seconds\": ").append(String.format(java.util.Locale.ROOT, "%.2f", wallSeconds)).append(",\n");
        bench.append("  \"latency_ms\": {\"min\": ").append(latencies.get(0))
                .append(", \"avg\": ").append(String.format(java.util.Locale.ROOT, "%.1f", avg))
                .append(", \"p50\": ").append(percentile(latencies, 50))
                .append(", \"p90\": ").append(percentile(latencies, 90))
                .append(", \"p95\": ").append(p95)
                .append(", \"p99\": ").append(percentile(latencies, 99))
                .append(", \"max\": ").append(latencies.get(latencies.size() - 1)).append("},\n");
        bench.append("  \"sla_p95_under_3000_ms\": ").append(p95 < 3000).append(",\n");
        bench.append("  \"source_breakdown\": {")
                .append(sourceCount.entrySet().stream()
                        .map(e -> json(e.getKey()) + ": " + e.getValue()).collect(Collectors.joining(", ")))
                .append("},\n");
        bench.append("  \"fallback_reason_breakdown\": {")
                .append(reasons.entrySet().stream()
                        .map(e -> json(e.getKey()) + ": " + e.getValue()).collect(Collectors.joining(", ")))
                .append("},\n");
        bench.append("  \"empty_suggestion_responses\": ").append(emptyResponses).append("\n}\n");
        Files.writeString(out.resolve("ds2-ai-latency-benchmark.json"), bench.toString(), StandardCharsets.UTF_8);

        Files.writeString(out.resolve("ds2-ai-demo-scenarios.json"),
                "{\n  \"task\": \"S2-08 TSK-02-07\",\n  \"passed\": " + passed[0] + ",\n  \"total\": " + steps.size()
                        + ",\n  \"scenarios\": [\n" + String.join(",\n", scenarios) + "\n  ]\n}\n",
                StandardCharsets.UTF_8);

        assertTrue(p95 < 3000, "NFR-03: p95 must stay under 3000 ms, was " + p95);
        assertEquals(100, latencies.size());
    }
}
