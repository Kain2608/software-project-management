package com.smartelectronicsupermarket.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import com.smartelectronicsupermarket.ai.service.AiCircuitBreaker;
import com.smartelectronicsupermarket.ai.service.AiProductNotFoundException;
import com.smartelectronicsupermarket.ai.service.AiScopeDeniedException;
import com.smartelectronicsupermarket.ai.summary.AiSpecSummaryService;
import com.smartelectronicsupermarket.ai.summary.MockSpecSummaryLlm;
import com.smartelectronicsupermarket.ai.summary.SpecProduct;
import com.smartelectronicsupermarket.ai.summary.SpecSummaryDataAccess;
import com.smartelectronicsupermarket.ai.summary.SpecSummaryLlm;
import com.smartelectronicsupermarket.ai.summary.SpecSummaryRequest;
import com.smartelectronicsupermarket.ai.summary.SpecSummaryResult;
import com.smartelectronicsupermarket.security.authorization.PermissionChecker;

/**
 * Produces the Sprint 3 (S3-03 / DS3) evidence files by really running {@link AiSpecSummaryService}.
 * Disabled unless SASMS_AI_EVIDENCE_DIR_S3 is set. The LLM is a deterministic/fault-injecting stand-in and the
 * catalog is an in-memory fixture: this proves the anti-fabrication pipeline, not a remote model or PostgreSQL.
 */
class AiSprint3EvidenceTest {

    private static final UUID ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000811");
    private static final UUID STORE = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:[.,]\\d+)?");

    private static final Map<UUID, SpecProduct> CATALOG = new HashMap<>();

    private static UUID add(String sku, String name, String category, boolean active, String specs) {
        UUID id = UUID.nameUUIDFromBytes(sku.getBytes(StandardCharsets.UTF_8));
        CATALOG.put(id, new SpecProduct(id, sku, name, "SASMS-FIXTURE", category, active, specs));
        return id;
    }

    /** Stand-in for the model. Behaviour is chosen by a marker in the product name, as seen in the prompt. */
    private static final java.util.concurrent.atomic.AtomicInteger LLM_CALLS = new java.util.concurrent.atomic.AtomicInteger();

    private static class ScriptedLlm implements SpecSummaryLlm {
        private final MockSpecSummaryLlm honest = new MockSpecSummaryLlm();

        @Override public String providerName() { return "MOCK"; }
        @Override public String modelVersion() { return "mock-spec-v1"; }

        @Override
        public String complete(String system, String user) {
            LLM_CALLS.incrementAndGet();
            String nameLine = user.lines().filter(l -> l.startsWith("- Tên sản phẩm:")).findFirst().orElse("");
            try {
                if (nameLine.contains("HANG")) {
                    Thread.sleep(5_000);
                } else {
                    Thread.sleep(700 + Math.floorMod(nameLine.hashCode(), 700));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (nameLine.contains("ERR")) {
                throw new IllegalStateException("simulated provider 503");
            }
            if (nameLine.contains("FAKE")) {
                return "{\"status\":\"SUCCESS\",\"highlights\":[\"Pin 5000 mAh dùng 2 ngày\",\"Chống nước IP68\","
                        + "\"Hỗ trợ 5G\"],\"target_audience\":\"Game thủ\"}";
            }
            if (nameLine.contains("MIXED")) {
                return "{\"status\":\"SUCCESS\",\"highlights\":[\"Chip A18 mạnh mẽ\",\"Pin 5000 mAh dùng 2 ngày\","
                        + "\"Màn hình OLED có Face ID\"],\"target_audience\":\"Người dùng phổ thông\"}";
            }
            if (nameLine.contains("BADJSON")) {
                return "Xin lỗi, tôi không thể tóm tắt sản phẩm này.";
            }
            return honest.complete(system, user);
        }
    }

    private static AiSpecSummaryService newService(PermissionChecker permissions) {
        SpecSummaryDataAccess data = mock(SpecSummaryDataAccess.class);
        when(data.findProduct(any())).thenAnswer(i -> Optional.ofNullable(CATALOG.get((UUID) i.getArgument(0))));
        return new AiSpecSummaryService(new ScriptedLlm(), data, permissions, new AiCircuitBreaker(),
                System::currentTimeMillis);
    }

    private static PermissionChecker allowAll() {
        PermissionChecker permissions = mock(PermissionChecker.class);
        when(permissions.hasPermission(any(), eq("ai.use"), eq(STORE))).thenReturn(true);
        return permissions;
    }

    private static SpecSummaryResult run(AiSpecSummaryService service, UUID product) {
        return service.summarize(ACTOR, new SpecSummaryRequest(STORE.toString(), product.toString(), "vi"));
    }

    private static boolean numbersGrounded(SpecSummaryResult result) {
        String corpus = String.join(" ", result.specifications().keySet()) + " "
                + String.join(" ", result.specifications().values());
        List<String> sources = new ArrayList<>();
        Matcher source = NUMBER.matcher(corpus);
        while (source.find()) {
            sources.add(source.group().replace(',', '.'));
        }
        for (String highlight : result.highlights()) {
            Matcher m = NUMBER.matcher(highlight);
            while (m.find()) {
                if (!sources.contains(m.group().replace(',', '.'))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static long percentile(List<Long> sorted, double p) {
        int rank = (int) Math.ceil(p / 100.0 * sorted.size());
        return sorted.get(Math.max(0, rank - 1));
    }

    private static String json(String s) {
        return s == null ? "null" : "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String arr(List<String> values) {
        return values.stream().map(AiSprint3EvidenceTest::json).collect(Collectors.joining(", ", "[", "]"));
    }

    private static String obj(Map<String, String> values) {
        return values.entrySet().stream().map(e -> json(e.getKey()) + ": " + json(e.getValue()))
                .collect(Collectors.joining(", ", "{", "}"));
    }

    @Test
    void generateSprint3Evidence() throws Exception {
        String dir = System.getenv("SASMS_AI_EVIDENCE_DIR_S3");
        assumeTrue(dir != null && !dir.isBlank(), "set SASMS_AI_EVIDENCE_DIR_S3 to generate evidence");
        Path out = Paths.get(dir);
        Files.createDirectories(out);

        UUID phone = add("FIX-PHONE-01", "Điện thoại FIX 6.1 inch", "SMARTPHONE", true,
                "{\"screen_inches\":6.1,\"display\":\"OLED\",\"ram_gb\":8,\"storage_gb\":128,\"chip\":\"A18\",\"battery_mah\":3561}");
        UUID laptop = add("FIX-LAPTOP-01", "Laptop FIX 14 inch", "LAPTOP", true,
                "{\"screen_inches\":14,\"resolution\":\"1920x1200\",\"cpu\":\"Core i5-1335U\",\"ram_gb\":16,\"ssd_gb\":512,\"weight_kg\":1.4}");
        UUID tv = add("FIX-TV-55", "Tivi FIX 55 inch", "TELEVISION", true,
                "{\"screen_inches\":55,\"resolution\":\"4K\",\"panel\":\"QLED\",\"refresh_hz\":120,\"hdmi_ports\":3}");
        UUID washer = add("FIX-WASH-01", "Máy giặt FIX 9 kg", "WASHING_MACHINE", true,
                "{\"capacity_kg\":9,\"motor\":\"Inverter\",\"spin_rpm\":1400,\"energy_label\":\"5 sao\"}");
        UUID fridge = add("FIX-FRIDGE-01", "Tủ lạnh FIX 320 lít", "REFRIGERATOR", true,
                "{\"capacity_liters\":320,\"compressor\":\"Inverter\",\"doors\":2,\"power_w\":150}");
        UUID aircon = add("FIX-AC-01", "Điều hòa FIX 12000 BTU", "AIR_CONDITIONER", true,
                "{\"capacity_btu\":12000,\"compressor\":\"Inverter\",\"noise_db\":21,\"power_w\":1050}");
        List<UUID> samples = List.of(phone, laptop, tv, washer, fridge, aircon);

        UUID fake = add("FIX-FAKE", "Điện thoại FAKE", "SMARTPHONE", true, "{\"chip\":\"A18\",\"ram_gb\":8,\"display\":\"OLED\"}");
        UUID mixed = add("FIX-MIXED", "Điện thoại MIXED", "SMARTPHONE", true, "{\"chip\":\"A18\",\"ram_gb\":8,\"display\":\"OLED\"}");
        UUID bad = add("FIX-BADJSON", "Điện thoại BADJSON", "SMARTPHONE", true, "{\"chip\":\"A18\",\"ram_gb\":8}");
        UUID err = add("FIX-ERR", "Điện thoại ERR", "SMARTPHONE", true, "{\"chip\":\"A18\",\"ram_gb\":8}");
        UUID hang = add("FIX-HANG-0", "Điện thoại HANG 0", "SMARTPHONE", true, "{\"chip\":\"A18\",\"ram_gb\":8}");
        UUID empty = add("FIX-EMPTY", "Phụ kiện không có thông số", "ACCESSORY", true, "{}");
        UUID inactive = add("FIX-OFF", "Sản phẩm ngừng bán", "SMARTPHONE", false, "{\"chip\":\"A18\"}");
        for (int i = 1; i <= 10; i++) {
            add("FIX-HANG-" + i, "Điện thoại HANG " + i, "SMARTPHONE", true, "{\"chip\":\"A18\",\"ram_gb\":8}");
        }

        Logger serviceLogger = (Logger) LoggerFactory.getLogger(AiSpecSummaryService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        serviceLogger.addAppender(appender);
        List<String> sourceLog = new ArrayList<>();
        sourceLog.add("# SASMS Sprint 3 S3-03 - AI spec summary source log (real execution of AiSpecSummaryService)");
        sourceLog.add("# Generated: " + Instant.now());
        sourceLog.add("# LLM = scripted stand-in (no remote provider); catalog = in-memory fixture; no PostgreSQL.");
        sourceLog.add("");
        sourceLog.add("## Parts A and B - six category samples, then 11 scenarios (sequential)");

        // ---------------- Part A: samples per category ----------------
        AiSpecSummaryService demo = newService(allowAll());
        List<String> sampleJson = new ArrayList<>();
        int groundedCount = 0;
        for (UUID id : samples) {
            SpecSummaryResult r = run(demo, id);
            boolean grounded = numbersGrounded(r);
            if (grounded && r.disclaimer().contains("chỉ mang tính tham khảo") && "AI".equals(r.source())) {
                groundedCount++;
            }
            sampleJson.add("    {\"sku\": " + json(CATALOG.get(id).sku()) + ", \"category\": " + json(CATALOG.get(id).category())
                    + ", \"source\": " + json(r.source()) + ", \"status\": " + json(r.status())
                    + ", \"specifications\": " + obj(r.specifications())
                    + ", \"highlights\": " + arr(r.highlights())
                    + ", \"target_audience\": " + json(r.targetAudience())
                    + ", \"disclaimer\": " + json(r.disclaimer())
                    + ", \"all_numbers_found_in_specs\": " + grounded + "}");
        }

        // ---------------- Part B: scenarios ----------------
        record Step(String id, String name, String expected, Callable<String> action) {
        }
        List<Step> steps = new ArrayList<>();
        steps.add(new Step("SUM-01", "Tóm tắt mới qua LLM, đã kiểm định (cache miss)", "AI",
                () -> run(newService(allowAll()), phone).source()));
        steps.add(new Step("SUM-02", "Lần 2 cùng sản phẩm, thông số không đổi -> cache hit", "CACHE",
                () -> run(demo, phone).source()));
        steps.add(new Step("SUM-03", "LLM bịa thông số (pin 5000 mAh, IP68, 5G) -> bị loại, dùng luật dự phòng", "RULE_FALLBACK",
                () -> run(demo, fake).source()));
        steps.add(new Step("SUM-04", "LLM bịa một phần: giữ ý đúng (chip A18), loại ý bịa", "AI:1",
                () -> {
                    SpecSummaryResult r = run(demo, mixed);
                    return r.source() + ":" + r.highlights().size();
                }));
        steps.add(new Step("SUM-05", "LLM trả văn bản không phải JSON -> luật dự phòng", "RULE_FALLBACK",
                () -> run(demo, bad).source()));
        steps.add(new Step("SUM-06", "LLM treo > 2,5 s -> luật dự phòng, dưới 3 s", "RULE_FALLBACK:true",
                () -> {
                    SpecSummaryResult r = run(demo, hang);
                    return r.source() + ":" + (r.tookMs() < 3000);
                }));
        steps.add(new Step("SUM-07", "LLM lỗi 503 -> luật dự phòng, vẫn có bảng thông số gốc", "RULE_FALLBACK:2",
                () -> {
                    SpecSummaryResult r = run(demo, err);
                    return r.source() + ":" + r.specifications().size();
                }));
        steps.add(new Step("SUM-08", "Thiếu thông số -> INSUFFICIENT_DATA, không gọi LLM (AC-09-2)", "INSUFFICIENT_DATA",
                () -> run(demo, empty).status()));
        steps.add(new Step("SUM-09", "Thu ngân không có ai.use tại store -> 403", "FORBIDDEN_SCOPE", () -> {
            try {
                newService(mock(PermissionChecker.class)).summarize(ACTOR,
                        new SpecSummaryRequest(STORE.toString(), phone.toString(), "vi"));
                return "NO_ERROR";
            } catch (AiScopeDeniedException e) {
                return "FORBIDDEN_SCOPE";
            }
        }));
        steps.add(new Step("SUM-10", "Sản phẩm ngừng bán -> 422 PRODUCT_NOT_FOUND", "PRODUCT_NOT_FOUND", () -> {
            try {
                run(demo, inactive);
                return "NO_ERROR";
            } catch (AiProductNotFoundException e) {
                return "PRODUCT_NOT_FOUND";
            }
        }));
        steps.add(new Step("SUM-11", "Mọi phản hồi có cảnh báo 'chỉ mang tính tham khảo'", "true",
                () -> String.valueOf(samples.stream().allMatch(id ->
                        run(demo, id).disclaimer().contains("chỉ mang tính tham khảo")))));

        List<String> scenarios = new ArrayList<>();
        int passed = 0;
        for (Step step : steps) {
            String actual;
            try {
                actual = step.action().call();
            } catch (Exception e) {
                actual = "EXCEPTION:" + e.getClass().getSimpleName();
            }
            boolean ok = step.expected().equals(actual);
            if (ok) {
                passed++;
            }
            scenarios.add("    {\"id\": " + json(step.id()) + ", \"name\": " + json(step.name()) + ", \"expected\": "
                    + json(step.expected()) + ", \"actual\": " + json(actual) + ", \"result\": "
                    + json(ok ? "PASS" : "FAIL") + "}");
        }

        for (ILoggingEvent event : new ArrayList<>(appender.list)) {
            if (event.getFormattedMessage().startsWith("[AI-AUDIT]")) {
                sourceLog.add(event.getFormattedMessage());
            }
        }
        appender.list.clear(); // Part C statistics must count the benchmark requests only.

        // ---------------- Part C: benchmark 100 requests / 50 concurrent ----------------
        AiSpecSummaryService loaded = newService(allowAll());
        LLM_CALLS.set(0);
        List<UUID> workload = new ArrayList<>();
        for (int i = 0; i < 90; i++) {
            workload.add(samples.get(i % samples.size()));
        }
        for (int i = 1; i <= 10; i++) {
            workload.add(UUID.nameUUIDFromBytes(("FIX-HANG-" + i).getBytes(StandardCharsets.UTF_8)));
        }
        Collections.shuffle(workload, new Random(20251107L));
        ExecutorService pool = Executors.newFixedThreadPool(50);
        List<Future<Object[]>> futures = new ArrayList<>();
        long wallStart = System.nanoTime();
        for (UUID id : workload) {
            futures.add(pool.submit(() -> {
                long start = System.nanoTime();
                SpecSummaryResult r = run(loaded, id);
                return new Object[] {r.source(), Math.round((System.nanoTime() - start) / 1_000_000.0),
                        r.specifications().isEmpty() ? 0 : 1};
            }));
        }
        List<Long> latencies = new ArrayList<>();
        Map<String, Integer> sources = new TreeMap<>();
        int withoutTable = 0;
        for (Future<Object[]> f : futures) {
            Object[] row = f.get();
            latencies.add((Long) row[1]);
            sources.merge((String) row[0], 1, Integer::sum);
            if ((Integer) row[2] == 0) {
                withoutTable++;
            }
        }
        double wall = (System.nanoTime() - wallStart) / 1_000_000_000.0;
        int llmCalls = LLM_CALLS.get();
        pool.shutdown();
        Collections.sort(latencies);
        double avg = latencies.stream().mapToLong(Long::longValue).average().orElse(0);

        sourceLog.add("");
        sourceLog.add("## Part C - benchmark 100 requests / 50 concurrent workers (arrival order)");
        Map<String, Long> reasons = new TreeMap<>();
        for (ILoggingEvent event : new ArrayList<>(appender.list)) {
            String message = event.getFormattedMessage();
            if (message.startsWith("[AI-AUDIT]")) {
                sourceLog.add(message);
                reasons.merge(message.replaceAll(".*fallback_reason=(\\S+).*", "$1"), 1L, Long::sum);
            }
        }
        serviceLogger.detachAppender(appender);
        Files.write(out.resolve("ds3-ai-summary-source.log"), sourceLog, StandardCharsets.UTF_8);

        long p95 = percentile(latencies, 95);
        String bench = "{\n  \"task\": \"S3-03 TSK-03-01\",\n  \"generated_at\": " + json(Instant.now().toString())
                + ",\n  \"method\": \"JUnit AiSprint3EvidenceTest: real AiSpecSummaryService, scripted LLM stand-in, in-memory catalog (no remote LLM, no PostgreSQL)\","
                + "\n  \"requests\": " + latencies.size() + ",\n  \"concurrency\": 50,"
                + "\n  \"workload\": \"90 requests on 6 products (LLM 700-1400 ms on a miss, no request coalescing), 10 requests on 10 distinct products whose LLM hangs 5 s\","
                + "\n  \"llm_calls_executed\": " + llmCalls + ",\n  \"distinct_cache_keys\": 16,"
                + "\n  \"wall_clock_seconds\": " + String.format(Locale.ROOT, "%.2f", wall)
                + ",\n  \"latency_ms\": {\"min\": " + latencies.get(0) + ", \"avg\": " + String.format(Locale.ROOT, "%.1f", avg)
                + ", \"p50\": " + percentile(latencies, 50) + ", \"p90\": " + percentile(latencies, 90)
                + ", \"p95\": " + p95 + ", \"p99\": " + percentile(latencies, 99) + ", \"max\": "
                + latencies.get(latencies.size() - 1) + "},\n  \"sla_p95_under_3000_ms\": " + (p95 < 3000)
                + ",\n  \"source_breakdown\": {"
                + sources.entrySet().stream().map(e -> json(e.getKey()) + ": " + e.getValue()).collect(Collectors.joining(", "))
                + "},\n  \"fallback_reason_breakdown\": {"
                + reasons.entrySet().stream().map(e -> json(e.getKey()) + ": " + e.getValue()).collect(Collectors.joining(", "))
                + "},\n  \"responses_without_spec_table\": " + withoutTable + "\n}\n";
        Files.writeString(out.resolve("ds3-ai-summary-latency-benchmark.json"), bench, StandardCharsets.UTF_8);

        Files.writeString(out.resolve("ds3-ai-summary-samples.json"),
                "{\n  \"task\": \"S3-03 TSK-03-01\",\n  \"note\": \"Fixture catalog (not production data). LLM is a deterministic stand-in.\","
                        + "\n  \"grounded_ai_samples\": " + groundedCount + ",\n  \"total\": " + samples.size()
                        + ",\n  \"samples\": [\n" + String.join(",\n", sampleJson) + "\n  ]\n}\n", StandardCharsets.UTF_8);
        Files.writeString(out.resolve("ds3-ai-summary-scenarios.json"),
                "{\n  \"task\": \"S3-03 TSK-03-01\",\n  \"passed\": " + passed + ",\n  \"total\": " + steps.size()
                        + ",\n  \"scenarios\": [\n" + String.join(",\n", scenarios) + "\n  ]\n}\n", StandardCharsets.UTF_8);

        assertEquals(steps.size(), passed, "every S3-03 scenario must pass");
        assertEquals(samples.size(), groundedCount);
        assertEquals(100, latencies.size());
        assertTrue(p95 < 3000, "AI < 3 s (p95), was " + p95);
    }
}
