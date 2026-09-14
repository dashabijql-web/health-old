package com.xzkj.health.common.exception;

import java.io.IOException;

public final class ClientAbortExceptions {

    private ClientAbortExceptions() {
    }

    public static boolean isClientAbort(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String name = current.getClass().getName();
            if (name.endsWith("ClientAbortException")
                    || name.endsWith("AsyncRequestNotUsableException")
                    || name.endsWith("ClientAbortException$1")) {
                return true;
            }
            String message = current.getMessage();
            if (current instanceof IOException && message != null) {
                String normalized = message.toLowerCase();
                if (normalized.contains("broken pipe")
                        || normalized.contains("connection reset")
                        || normalized.contains("abort")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }
}