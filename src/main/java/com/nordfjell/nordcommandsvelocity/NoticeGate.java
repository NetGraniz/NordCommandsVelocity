package com.nordfjell.nordcommandsvelocity;

import java.util.IdentityHashMap;
import java.util.Map;

/** Bounded session-identity state. Overflow suppresses notices, never denial. */
final class NoticeGate {
    static final int CAPACITY = 4096;
    static final long INTERVAL_NANOS = 250_000_000L;
    private final Map<Object, Long> lastNotice = new IdentityHashMap<>();
    private boolean closed;

    synchronized boolean admit(Object session, long now) {
        if (closed || session == null) return false;
        Long previous = lastNotice.get(session);
        if (previous != null && now - previous < INTERVAL_NANOS) return false;
        if (previous == null && lastNotice.size() >= CAPACITY) return false;
        lastNotice.put(session, now);
        return true;
    }
    synchronized void remove(Object session) { lastNotice.remove(session); }
    synchronized void close() { closed = true; lastNotice.clear(); }
    synchronized int size() { return lastNotice.size(); }
}
