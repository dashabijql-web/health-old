package com.xzkj.health.controller;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

class CoreControllerBoundaryTest {

    private static final List<String> CORE_CONTROLLERS = List.of(
            "DashboardController.java",
            "RealtimeController.java",
            "HealthPortraitController.java",
            "TrendWarningController.java",
            "StatisticsController.java"
    );

    @Test
    void coreControllersDoNotBypassServiceOrGlobalExceptionBoundary() throws IOException {
        Path controllerDir = Path.of("src/main/java/com/xzkj/health/controller");

        for (String controller : CORE_CONTROLLERS) {
            String source = Files.readString(controllerDir.resolve(controller));

            assertFalse(source.contains(".mapper."), controller + " must not import mapper classes");
            assertFalse(source.contains("Result.error"), controller + " must use global exception handling");
            assertFalse(source.contains("Map<String, Object>"), controller + " must not expose raw Map contracts");
            assertFalse(source.contains("catch (Exception"), controller + " must not catch broad exceptions");
            assertFalse(source.contains("catch (IllegalStateException"), controller + " must not translate service exceptions locally");
        }
    }
}
