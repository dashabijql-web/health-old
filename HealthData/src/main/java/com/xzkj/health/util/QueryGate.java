package com.xzkj.health.util;

import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

/** Serializes heavy dashboard scans so one refresh does not open several monthly-table UNION queries at once. */
public final class QueryGate {

    private static final Semaphore HEALTH_RECORD = new Semaphore(1);

    private QueryGate() {
    }

    public static <T> T healthRecord(Supplier<T> loader) {
        HEALTH_RECORD.acquireUninterruptibly();
        try {
            return loader.get();
        } finally {
            HEALTH_RECORD.release();
        }
    }
}