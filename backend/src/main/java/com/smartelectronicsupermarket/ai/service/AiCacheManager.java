package com.smartelectronicsupermarket.ai.service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.smartelectronicsupermarket.ai.dto.CrossSellItem;

@Component
public class AiCacheManager {

    private static final Logger log = LoggerFactory.getLogger(AiCacheManager.class);
    private static final long DEFAULT_TTL_MS = 5 * 60 * 1000L; // 5 minutes per D55

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final AtomicLong hitCount = new AtomicLong(0);
    private final AtomicLong missCount = new AtomicLong(0);

    public List<CrossSellItem> get(String key) {
        CacheEntry entry = cache.get(key);
        if (entry != null && !entry.isExpired()) {
            hitCount.incrementAndGet();
            log.debug("[AiCache] Cache HIT for key: {}", key);
            return entry.items();
        }
        if (entry != null && entry.isExpired()) {
            cache.remove(key);
        }
        missCount.incrementAndGet();
        log.debug("[AiCache] Cache MISS for key: {}", key);
        return null;
    }

    public void put(String key, List<CrossSellItem> items) {
        cache.put(key, new CacheEntry(items, System.currentTimeMillis() + DEFAULT_TTL_MS));
        log.debug("[AiCache] Stored {} items in cache with key: {}", items.size(), key);
    }

    public void clear() {
        cache.clear();
    }

    public long getHitCount() {
        return hitCount.get();
    }

    public long getMissCount() {
        return missCount.get();
    }

    public int size() {
        return cache.size();
    }

    private record CacheEntry(List<CrossSellItem> items, long expiresAt) {
        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }
}
