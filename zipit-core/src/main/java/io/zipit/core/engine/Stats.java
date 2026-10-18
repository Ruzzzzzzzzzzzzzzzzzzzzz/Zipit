package io.zipit.core.engine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Thread-safe counter bag populated by passes and consumed by audit reports. */
public final class Stats {

    private final Map<String, Long> values = new LinkedHashMap<String, Long>();

    public synchronized void add(String key, long delta) {
        Long current = values.get(key);
        values.put(key, Long.valueOf((current == null ? 0L : current.longValue()) + delta));
    }

    public void inc(String key) {
        add(key, 1L);
    }

    public synchronized Map<String, Long> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<String, Long>(values));
    }
}
