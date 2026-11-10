package dev.nexcraft.maegae.processor;

import dev.nexcraft.maegae.annotation.Tool;
import dev.nexcraft.maegae.internal.json.JacksonJsonCodec;
import dev.nexcraft.maegae.internal.json.JsonArray;
import dev.nexcraft.maegae.internal.json.JsonObject;
import dev.nexcraft.maegae.internal.json.JsonString;
import dev.nexcraft.maegae.protocol.mcp.ToolDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.StringWriter;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Compiles generated metadata and checks its observable JSON Schema semantics. */
class ToolMetadataGenerationTest {
    private static final String IMPORTS = "import dev.nexcraft.maegae.annotation.*;\n";

    @Test
    void generatesCompilableImmutableMetadataForAllSupportedTypes(@TempDir final Path output) throws Exception {
        final var result = compile(output, IMPORTS + """
                import java.math.*;
                class ExampleTools {
                    @Tool public void empty() {}
                    @Tool(name = "custom.name", description = "quote \\\" slash \\\\ newline \\n 한 😀")
                    public void all(
                            @Arg(name = "a\\\"\\\\", description = "desc \\n 한 😀", required = true) String text,
                            boolean bool, Boolean boxedBool, byte b, Byte boxedByte, short s, Short boxedShort,
                            int i, Integer boxedInt, long l, Long boxedLong, float f, Float boxedFloat,
                            double d, Double boxedDouble, BigInteger bigInt,
                            @Arg(required = false) BigDecimal decimal) {}
                }
                """);
        assertTrue(result.success(), result.diagnostics().toString());
        final List<ToolDefinition> definitions = definitions(result, "ExampleTools");
        assertEquals(List.of("custom.name", "empty"), definitions.stream().map(ToolDefinition::name).toList());
        assertThrows(UnsupportedOperationException.class, () -> definitions.add(definitions.getFirst()));
        final ToolDefinition all = definitions.getFirst();
        assertEquals(Optional.of("quote \" slash \\ newline \n 한 😀"), all.description());
        final JsonObject schema = (JsonObject) new JacksonJsonCodec().parse(all.inputSchemaJson());
        assertEquals(new JsonString("object"), schema.values().get("type"));
        final JsonObject properties = (JsonObject) schema.values().get("properties");
        final List<String> names = List.of("a\"\\", "bool", "boxedBool", "b", "boxedByte", "s", "boxedShort",
                "i", "boxedInt", "l", "boxedLong", "f", "boxedFloat", "d", "boxedDouble", "bigInt", "decimal");
        assertEquals(names, List.copyOf(properties.values().keySet()));
        final List<String> types = List.of("string", "boolean", "boolean", "integer", "integer", "integer", "integer",
                "integer", "integer", "integer", "integer", "number", "number", "number", "number", "integer", "number");
        for (int index = 0; index < names.size(); index++) {
            final JsonObject property = (JsonObject) properties.values().get(names.get(index));
            assertEquals(new JsonString(types.get(index)), property.values().get("type"));
            if (index == 0) {
                assertEquals(new JsonString("desc \n 한 😀"), property.values().get("description"));
            } else {
                assertFalse(property.values().containsKey("description"));
            }
        }
        final JsonArray required = (JsonArray) schema.values().get("required");
        assertEquals(names.subList(0, names.size() - 1), required.values().stream()
                .map(value -> ((JsonString) value).value()).toList());
        final ToolDefinition empty = definitions.getLast();
        assertEquals(Optional.empty(), empty.description());
        final JsonObject emptySchema = (JsonObject) new JacksonJsonCodec().parse(empty.inputSchemaJson());
        assertEquals(new JsonObject(java.util.Map.of()), emptySchema.values().get("properties"));
        assertEquals(new JsonArray(List.of()), emptySchema.values().get("required"));
    }

    @Test
    void distinguishesNestedAndTopLevelProvidersAndProducesDeterministicOutput(@TempDir final Path output)
            throws Exception {
        final String source = IMPORTS + """
                class ExampleTools {
                    @Tool public void first() {}
                    static class Nested { @Tool(name = "same") public void nested() {} }
                }
                class ExampleTools_Nested { @Tool(name = "same") public void other() {} }
                """;
        final var first = compile(Files.createDirectory(output.resolve("first")), source);
        final var second = compile(Files.createDirectory(output.resolve("second")), source);
        assertTrue(first.success(), first.diagnostics().toString());
        assertTrue(second.success(), second.diagnostics().toString());
        assertEquals("same", definitions(first, "ExampleTools$Nested").getFirst().name());
        assertEquals("same", definitions(first, "ExampleTools_Nested").getFirst().name());
        try (final var files = Files.walk(first.generated())) {
            final var sources = files.filter(Files::isRegularFile).sorted().toList();
            assertEquals(3, sources.size());
            for (final Path sourceFile : sources) {
                assertEquals(Files.readString(sourceFile),
                        Files.readString(second.generated().resolve(first.generated().relativize(sourceFile))));
            }
        }
    }

    @Test
    void generatesMetadataForProviderWithVeryLongBinaryName(@TempDir final Path output) throws Exception {
        final String packageName = "p".repeat(60) + "." + "q".repeat(60);
        final String binaryName = packageName + "." + "Outer".repeat(8) + "$" + "Inner".repeat(8);
        final var result = compile(output, "package " + packageName + ";\n" + IMPORTS
                + "class " + "Outer".repeat(8) + " { static class " + "Inner".repeat(8)
                + " { @Tool public void call() {} } }");
        assertTrue(result.success(), result.diagnostics().toString());
        assertEquals("call", definitions(result, binaryName).getFirst().name());
    }

    @Test
    void reportsCollisionWithAnExistingSource(@TempDir final Path output) throws Exception {
        final String generatedName = "MaegaeToolMetadata_" + new GeneratedNames().suffix("ExampleTools");
        final var result = compile(output, IMPORTS + "class ExampleTools { @Tool public void call() {} }\n"
                + "class " + generatedName + " {}\n");
        assertFalse(result.success());
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.getMessage(Locale.ROOT)
                .contains("Cannot generate Tool metadata '" + generatedName + "'")), result.diagnostics().toString());
    }

    @Test
    void reportsGeneratedNameSuffixCollisionBetweenProviders(@TempDir final Path output) throws Exception {
        final var result = compile(output, IMPORTS + """
                class First { @Tool public void call() {} }
                class Second { @Tool public void call() {} }
                """, List.of(new McpToolProcessor(new GeneratedNames(binaryName -> "same"))));
        assertFalse(result.success());
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.getMessage(Locale.ROOT)
                .contains("suffix 'same' is shared by providers 'First' and 'Second'")),
                result.diagnostics().toString());
        try (final var generated = Files.walk(result.generated())) {
            assertEquals(1, generated.filter(Files::isRegularFile).count());
        }
    }

    @Test
    void generatesMetadataForProvidersInLaterRounds(@TempDir final Path output) throws Exception {
        final var result = compile(output, IMPORTS + "class ExampleTools { @Tool public void first() {} }", true);
        assertTrue(result.success(), result.diagnostics().toString());
        assertEquals("first", definitions(result, "ExampleTools").getFirst().name());
        assertEquals("later", definitions(result, "LaterTools").getFirst().name());
        try (final var files = Files.walk(result.generated())) {
            assertEquals(3, files.filter(Files::isRegularFile).count());
        }
    }

    @Test
    void unsupportedArgumentPreventsPartialMetadataForItsProvider(@TempDir final Path output) throws Exception {
        final var result = compile(output, IMPORTS + """
                class ExampleTools {
                    @Tool public void valid(String text) {}
                    @Tool public void unsupported(java.util.List<String> items) {}
                }
                """);
        assertFalse(result.success());
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.getMessage(Locale.ROOT)
                .contains("Unsupported MCP Tool argument type")), result.diagnostics().toString());
        try (final var generated = Files.walk(result.generated())) {
            assertEquals(0, generated.filter(Files::isRegularFile).count());
        }
    }

    @Test
    void preservesLongSupplementaryUnicodeDescription(@TempDir final Path output) throws Exception {
        final String description = "😀".repeat(5500);
        final var result = compile(output, IMPORTS + "class ExampleTools { @Tool public void call("
                + "@Arg(description = \"" + description + "\") String value) {} }");
        assertTrue(result.success(), result.diagnostics().toString());
        final var schema = (JsonObject) new JacksonJsonCodec().parse(
                definitions(result, "ExampleTools").getFirst().inputSchemaJson());
        final var properties = (JsonObject) schema.values().get("properties");
        final var property = (JsonObject) properties.values().get("value");
        assertEquals(new JsonString(description), property.values().get("description"));
    }

    @Test
    void preservesLiteralBackslashUnicodeSequencesInNamedPackages(@TempDir final Path output) throws Exception {
        final String value = "\\" + "u000a";
        final String literal = "\\\\" + "u000a";
        final var result = compile(output, "package example;\n" + IMPORTS
                + "class ExampleTools { @Tool(description = \"" + literal + "\") public void call("
                + "@Arg(name = \"" + literal + "\", description = \"" + literal + "\") String item) {} }");
        assertTrue(result.success(), result.diagnostics().toString());
        final var definition = definitions(result, "example.ExampleTools").getFirst();
        assertEquals(Optional.of(value), definition.description());
        final var schema = (JsonObject) new JacksonJsonCodec().parse(definition.inputSchemaJson());
        final var properties = (JsonObject) schema.values().get("properties");
        final var property = (JsonObject) properties.values().get(value);
        assertEquals(new JsonString(value), property.values().get("description"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"java.util.List<String>", "String[]", "Object", "java.util.Optional<String>", "Thread.State"})
    void rejectsUnsupportedParameters(final String type, @TempDir final Path output) throws Exception {
        final var result = compile(output, IMPORTS + "class ExampleTools { @Tool public void process("
                + type + " items) {} }");
        assertFalse(result.success());
        final var errors = result.diagnostics().stream().filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                .filter(d -> d.getMessage(Locale.ROOT).contains("Unsupported MCP Tool argument type"))
                .toList();
        assertEquals(1, errors.size(), result.diagnostics().toString());
        assertTrue(errors.getFirst().getMessage(Locale.ROOT).contains("parameter 'items' in Tool 'process'"));
        assertTrue(errors.getFirst().getLineNumber() > 0);
        try (final var generated = Files.walk(result.generated())) {
            assertEquals(0, generated.filter(Files::isRegularFile).count());
        }
    }

    private static Compilation compile(final Path output, final String text) throws Exception {
        return compile(output, text, null);
    }

    private static Compilation compile(final Path output, final String text, final boolean multipleRounds)
            throws Exception {
        return compile(output, text, multipleRounds
                ? List.of(new McpToolProcessor(), new LaterProviderProcessor()) : null);
    }

    private static Compilation compile(final Path output, final String text,
                                       final List<? extends javax.annotation.processing.Processor> processors)
            throws Exception {
        final Path source = output.resolve("ExampleTools.java");
        final Path classes = Files.createDirectory(output.resolve("classes"));
        final Path generated = Files.createDirectory(output.resolve("generated"));
        Files.writeString(source, text, StandardCharsets.UTF_8);
        final Path core = Path.of(Tool.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        final var diagnostics = new DiagnosticCollector<JavaFileObject>();
        final var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler);
        try (final var files = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            final var task = compiler.getTask(new StringWriter(), files, diagnostics, List.of(
                    "--release", "25", "--class-path", core.toString(), "--processor-path",
                    System.getProperty("maegae.processor.jar") + File.pathSeparator + core,
                    "-d", classes.toString(), "-s", generated.toString()), null, files.getJavaFileObjects(source));
            if (processors != null) {
                task.setProcessors(processors);
            }
            task.setLocale(Locale.ROOT);
            return new Compilation(task.call(), List.copyOf(diagnostics.getDiagnostics()), classes, generated);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<ToolDefinition> definitions(final Compilation compilation, final String binaryName)
            throws Exception {
        final int separator = binaryName.lastIndexOf('.');
        final String packageName = separator < 0 ? "" : binaryName.substring(0, separator + 1);
        final String className = packageName + "MaegaeToolMetadata_" + new GeneratedNames().suffix(binaryName);
        try (final var loader = new URLClassLoader(new java.net.URL[]{compilation.classes().toUri().toURL()},
                ToolDefinition.class.getClassLoader())) {
            return (List<ToolDefinition>) loader.loadClass(className).getMethod("tools").invoke(null);
        }
    }

    private record Compilation(boolean success, List<Diagnostic<? extends JavaFileObject>> diagnostics,
                               Path classes, Path generated) { }

    /** Generates another provider to exercise metadata generation in a later round. */
    private static final class LaterProviderProcessor extends AbstractProcessor {
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
                try (final var writer = processingEnv.getFiler().createSourceFile("LaterTools").openWriter()) {
                    writer.write("import dev.nexcraft.maegae.annotation.Tool; "
                            + "class LaterTools { @Tool public void later() {} }");
                } catch (java.io.IOException exception) {
                    throw new AssertionError(exception);
                }
            }
            return false;
        }
    }
}
