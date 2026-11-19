package com.juniverse.redis.example;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Guards the dependency rule of the hexagonal architecture: the domain and the application
 * layers must not depend on the infrastructure, on Spring or on the Redis library.
 */
public class HexagonalArchitectureTest {

    private static final Path SOURCES = Path.of("src/main/java/com/juniverse/redis/example");

    private static final List<String> FORBIDDEN_IMPORTS = List.of(
            "import com.juniverse.redis.example.infrastructure",
            "import org.springframework",
            "import io.github.programming0026",
            "import com.fasterxml");

    @ParameterizedTest
    @ValueSource(strings = {"domain", "application"})
    public void coreLayersOnlyDependOnThemselves(String layer) throws IOException {
        Path root = SOURCES.resolve(layer);
        assertTrue(Files.isDirectory(root), "missing layer: " + root);

        try (Stream<Path> files = Files.walk(root)) {
            List<Path> sources = files.filter(path -> path.toString().endsWith(".java")).toList();
            assertTrue(!sources.isEmpty(), "no sources in " + root);
            for (Path source : sources) {
                for (String line : Files.readAllLines(source)) {
                    for (String forbidden : FORBIDDEN_IMPORTS) {
                        assertTrue(!line.startsWith(forbidden),
                                source.getFileName() + " must not depend on the outside: " + line);
                    }
                }
            }
        }
    }
}
