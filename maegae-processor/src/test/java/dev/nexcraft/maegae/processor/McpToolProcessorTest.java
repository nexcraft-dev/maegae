package dev.nexcraft.maegae.processor;

import dev.nexcraft.maegae.annotation.Tool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.lang.model.SourceVersion;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the actual packaged provider through javac service discovery, without production test hooks. */
class McpToolProcessorTest {
    private static final String PROVIDER = "dev.nexcraft.maegae.processor.McpToolProcessor";
    private static final String SERVICE = "META-INF/services/javax.annotation.processing.Processor";

    @Test
    void declaresOnlyToolSupportAndTheExactJava25Contract() {
        final var processor = new McpToolProcessor();

        assertEquals(Set.of(Tool.class.getCanonicalName()), processor.getSupportedAnnotationTypes());
        assertEquals(SourceVersion.RELEASE_25, processor.getSupportedSourceVersion());
    }

    @Test
    void javacDiscoversAndInvokesThePackagedProviderWithoutGeneratingToolFiles(@TempDir final Path output)
            throws Exception {
        assertPackagedService();
        final String trace = compile("""
                import dev.nexcraft.maegae.annotation.Tool;
                public class ExampleTools {
                    @Tool(description = "Example tool")
                    public String hello() { return "hello"; }

                    @Tool
                    public int add(int first, int second) { return first + second; }
                }
                """, output);

        final String invocation = trace.lines().filter(line -> line.startsWith("Processor " + PROVIDER + " "))
                .findFirst().orElseThrow(() -> new AssertionError("Missing processor invocation trace: " + trace));
        assertTrue(invocation.contains(Tool.class.getCanonicalName()), trace);
        assertTrue(invocation.contains("returns false"), trace);
        assertTrue(trace.contains("last round: true"), trace);
    }

    @ParameterizedTest
    @ValueSource(strings = {"@Deprecated public class ExampleTools {}", "public class ExampleTools {}"})
    void compilesUnrelatedAndUnannotatedSourcesWithoutToolProcessing(final String source,
                                                                    @TempDir final Path output) throws Exception {
        final String trace = compile(source, output);

        assertFalse(trace.contains("Processor " + PROVIDER + " "), trace);
    }

    /** Inspects the same Gradle-built JAR used by javac, not a copied test service resource. */
    private static void assertPackagedService() throws Exception {
        try (final var jar = new JarFile(processorJar().toFile())) {
            assertEquals(1L, jar.stream().filter(entry -> entry.getName().equals(SERVICE)).count());
            final var registration = jar.getJarEntry(SERVICE);
            assertNotNull(registration);
            try (final var input = jar.getInputStream(registration)) {
                assertEquals(PROVIDER + "\n", new String(input.readAllBytes(), StandardCharsets.UTF_8));
            }
            assertNotNull(jar.getJarEntry(PROVIDER.replace('.', '/') + ".class"));
        }
    }

    /** Compiles isolated source with service lookup enabled and asserts the complete output boundary. */
    private static String compile(final String sourceText, final Path output) throws Exception {
        final Path source = output.resolve("ExampleTools.java");
        final Path classes = Files.createDirectory(output.resolve("classes"));
        final Path generated = Files.createDirectory(output.resolve("generated"));
        Files.writeString(source, sourceText, StandardCharsets.UTF_8);
        final Path core = Path.of(Tool.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        final var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "Processor tests require the configured JDK 25 compiler");
        final var diagnostics = new DiagnosticCollector<JavaFileObject>();
        final var trace = new StringWriter();
        try (final var files = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            final var task = compiler.getTask(trace, files, diagnostics, List.of(
                    "--release", "25", "--class-path", core.toString(),
                    "--processor-path", processorJar() + File.pathSeparator + core,
                    "-XprintProcessorInfo", "-XprintRounds", "-d", classes.toString(), "-s", generated.toString()),
                    null, files.getJavaFileObjects(source));
            task.setLocale(Locale.ROOT);
            assertTrue(task.call(), trace + "\n" + diagnostics.getDiagnostics());
        }
        assertTrue(diagnostics.getDiagnostics().isEmpty(), diagnostics.getDiagnostics().toString());
        try (final var classFiles = Files.walk(classes); final var generatedFiles = Files.walk(generated)) {
            assertEquals(List.of(Path.of("ExampleTools.class")), classFiles.filter(Files::isRegularFile)
                    .map(classes::relativize).sorted().toList());
            assertTrue(generatedFiles.noneMatch(Files::isRegularFile));
        }
        return trace.toString();
    }

    /** Obtains the real archive path supplied by the Processor Gradle test task. */
    private static Path processorJar() {
        return Path.of(Objects.requireNonNull(System.getProperty("maegae.processor.jar"),
                "Gradle must provide the packaged Processor JAR"));
    }
}
