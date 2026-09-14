package com.xzkj.health.common.exception;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientAbortExceptionsTest {

    @Test
    void detectsBrokenPipeAndNestedAbort() {
        assertTrue(ClientAbortExceptions.isClientAbort(new IOException("Broken pipe")));
        assertTrue(ClientAbortExceptions.isClientAbort(new IOException("Connection reset by peer")));
        assertTrue(ClientAbortExceptions.isClientAbort(new RuntimeException(new IOException("Broken pipe"))));
        assertFalse(ClientAbortExceptions.isClientAbort(new IOException("disk full")));
        assertFalse(ClientAbortExceptions.isClientAbort(new IllegalStateException("boom")));
    }
}

class DatabaseAccessExceptionsTest {

    @Test
    void detectsClosedConnectionAndReadTimeout() {
        assertTrue(DatabaseAccessExceptions.isTimeoutOrClosed(
                new RuntimeException("JDBC rollback; The connection is closed.")));
        assertTrue(DatabaseAccessExceptions.isTimeoutOrClosed(
                new RuntimeException(new java.net.SocketTimeoutException("Read timed out"))));
        assertFalse(DatabaseAccessExceptions.isTimeoutOrClosed(new IllegalStateException("boom")));
    }
}