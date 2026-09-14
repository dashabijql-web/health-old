package com.xzkj.health.common.exception;

public final class DatabaseAccessExceptions {

    private DatabaseAccessExceptions() {
    }

    public static boolean isTimeoutOrClosed(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase();
                if (normalized.contains("connection is closed")
                        || normalized.contains("read timed out")
                        || normalized.contains("sockettimeout")
                        || normalized.contains("jdbc rollback")
                        || normalized.contains("jdbc commit")
                        || normalized.contains("query timeout")
                        || normalized.contains("recycle error")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }
}