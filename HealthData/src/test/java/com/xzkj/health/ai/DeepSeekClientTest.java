package com.xzkj.health.ai;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DeepSeekClientTest {

    @Test
    void candidateApiKeyFilesIncludesHyphenAndUnderscoreDesktopNames() throws Exception {
        DeepSeekClient client = new DeepSeekClient();
        Method method = DeepSeekClient.class.getDeclaredMethod("candidateApiKeyFiles");
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<Path> candidates = (List<Path>) method.invoke(client);

        assertTrue(candidates.stream().anyMatch(path -> path.endsWith(Path.of("Desktop", "deepseek-key.txt"))));
        assertTrue(candidates.stream().anyMatch(path -> path.endsWith(Path.of("Desktop", "deepseek_key.txt"))));
    }
}
