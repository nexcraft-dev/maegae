package dev.nexcraft.maegae.processor;

import dev.nexcraft.maegae.annotation.Tool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests the processor's compilation contract and error locations. */
class ToolValidationTest {
    private static final String IMPORTS = "import dev.nexcraft.maegae.annotation.*;\n";

    @Test
    void compilesValidProvidersAndArguments(@TempDir final Path output) throws Exception {
        final var result = compile(output, IMPORTS + """
                class ExampleTools {
                    @Tool public int add(int a, int b) throws java.io.IOException { return a + b; }
                    @Tool(name = "explicit.name") public void other(@Arg(name = "some key", required = false) Integer a) {}
                    @Tool(name = "unicode 이름") public void named(@Arg(description = "value") String value) {}
                    static class Nested {
                        @Tool public void nested() {}
                    }
                }
                class OtherTools {
                    @Tool public void add() {}
                }
                """, false);
        assertTrue(result.success(), result.diagnostics().toString());
        assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
        try (final var generated = Files.walk(output.resolve("generated"))) {
            assertEquals(6, generated.filter(Files::isRegularFile).count());
        }
    }

    @Test
    void localClassToolsAreOutsideRoundEnvironmentDiscovery(@TempDir final Path output) throws Exception {
        final var result = compile(output, IMPORTS + """
                class ExampleTools {
                    @Tool public void visible() {}
                    void outer() {
                        class Local {
                            @Tool public static void unsupported() {}
                        }
                    }
                }
                """, false);
        assertTrue(result.success(), result.diagnostics().toString());
        assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidDeclarations")
    void rejectsInvalidDeclarations(final String label, final String declaration, final String message,
                                    @TempDir final Path output) throws Exception {
        final var result = compile(output, IMPORTS + declaration, false);
        assertFalse(result.success(), label);
        final var matches = result.diagnostics().stream()
                .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
                .filter(diagnostic -> diagnostic.getMessage(Locale.ROOT).contains(message)).toList();
        assertEquals(1, matches.size(), result.diagnostics().toString());
        assertNotNull(matches.getFirst().getSource());
        assertTrue(matches.getFirst().getLineNumber() > 0, result.diagnostics().toString());
    }

    private static Stream<Arguments> invalidDeclarations() {
        return Stream.of(
                invalid("private", "class ExampleTools { @Tool private void call() {} }", "must be public"),
                invalid("protected", "class ExampleTools { @Tool protected void call() {} }", "must be public"),
                invalid("package", "class ExampleTools { @Tool void call() {} }", "must be public"),
                invalid("static", "class ExampleTools { @Tool public static void call() {} }", "not static"),
                invalid("abstract", "abstract class ExampleTools { @Tool public abstract void call(); }", "must not be abstract"),
                invalid("generic", "class ExampleTools { @Tool public <T> T call(T value) { return value; } }", "type parameters"),
                invalid("varargs", "class ExampleTools { @Tool public void call(String... values) {} }", "varargs"),
                invalid("private provider", "class ExampleTools { private static class Hidden { @Tool public void call() {} } }", "provider must be a class accessible"),
                invalid("duplicate name", "class ExampleTools { @Tool(name=\"same\") public void a() {} @Tool(name=\"same\") public void b() {} }", "Duplicate MCP Tool name 'same'"),
                invalid("overloads", "class ExampleTools { @Tool public void call(int a) {} @Tool public void call(int a, int b) {} }", "Duplicate MCP Tool name 'call'"),
                invalid("default and explicit name", "class ExampleTools { @Tool public void call() {} @Tool(name=\"call\") public void other() {} }", "Duplicate MCP Tool name 'call'"),
                invalid("duplicate argument", "class ExampleTools { @Tool public void call(@Arg(name=\"value\") int a, @Arg(name=\"value\") int b) {} }", "Duplicate MCP argument name 'value'"),
                invalid("default and explicit argument", "class ExampleTools { @Tool public void call(int value, @Arg(name=\"value\") int b) {} }", "Duplicate MCP argument name 'value'"),
                invalid("blank Tool name", "class ExampleTools { @Tool(name=\"   \") public void call() {} }", "Tool name must not be blank"),
                invalid("blank argument name", "class ExampleTools { @Tool public void call(@Arg(name=\"   \") int a) {} }", "argument name must not be blank"));
    }

    private static Arguments invalid(final String label, final String declaration, final String message) {
        return Arguments.of(label, declaration, message);
    }

    @Test
    void attachesDuplicateArgumentErrorToTheSecondParameter(@TempDir final Path output) throws Exception {
        final var result = compile(output, IMPORTS + """
                class ExampleTools {
                    @Tool public void call(
                            @Arg(name = "value") int first,
                            @Arg(name = "value") int second) {}
                }
                """, false);
        assertFalse(result.success());
        final var error = result.diagnostics().stream()
                .filter(diagnostic -> diagnostic.getMessage(Locale.ROOT).contains("Duplicate MCP argument name"))
                .findFirst().orElseThrow();
        assertEquals(5, error.getLineNumber());
    }

    @Test
    void reportsDuplicateOnceWhenAnotherProcessorCreatesAnotherRound(@TempDir final Path output) throws Exception {
        final var result = compile(output, IMPORTS + """
                class ExampleTools {
                    @Tool public void first() {}
                }
                """, true);
        assertFalse(result.success());
        final var matches = result.diagnostics().stream()
                .filter(diagnostic -> diagnostic.getMessage(Locale.ROOT).contains("Duplicate MCP Tool name 'same'"))
                .toList();
        assertEquals(1, matches.size(), result.diagnostics().toString());
        assertTrue(matches.getFirst().getLineNumber() > 0);
    }

    private static Compilation compile(final Path output, final String sourceText, final boolean multipleRounds)
            throws Exception {
        final Path source = output.resolve("ExampleTools.java");
        final Path classes = Files.createDirectory(output.resolve("classes"));
        final Path generated = Files.createDirectory(output.resolve("generated"));
        Files.writeString(source, sourceText, StandardCharsets.UTF_8);
        final Path core = Path.of(Tool.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        final var diagnostics = new DiagnosticCollector<JavaFileObject>();
        final var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler);
        try (final var files = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            final var task = compiler.getTask(new StringWriter(), files, diagnostics, List.of(
                    "--release", "25", "--class-path", core.toString(), "--processor-path",
                    System.getProperty("maegae.processor.jar") + File.pathSeparator + core,
                    "-d", classes.toString(), "-s", generated.toString()), null, files.getJavaFileObjects(source));
            if (multipleRounds) {
                task.setProcessors(List.of(new McpToolProcessor(), new GeneratingProcessor()));
            }
            task.setLocale(Locale.ROOT);
            return new Compilation(task.call(), List.copyOf(diagnostics.getDiagnostics()));
        }
    }

    private record Compilation(boolean success, List<Diagnostic<? extends JavaFileObject>> diagnostics) { }

    /** Creates a provider in a later processing round. */
    private static final class GeneratingProcessor extends AbstractProcessor {
        private boolean generated;

        @Override
        public Set<String> getSupportedAnnotationTypes() {
            return Set.of("*");
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.RELEASE_25;
        }

        @Override
        public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment round) {
            if (!generated && !round.processingOver()) {
                generated = true;
                try (final var writer = processingEnv.getFiler().createSourceFile("GeneratedTools").openWriter()) {
                    writer.write("import dev.nexcraft.maegae.annotation.Tool; class GeneratedTools { "
                            + "@Tool(name=\"same\") public void a() {} "
                            + "@Tool(name=\"same\") public void b() {} }");
                } catch (IOException exception) {
                    throw new AssertionError(exception);
                }
            }
            return false;
        }
    }
}
