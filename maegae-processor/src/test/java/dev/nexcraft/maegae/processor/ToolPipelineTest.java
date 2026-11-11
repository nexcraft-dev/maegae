package dev.nexcraft.maegae.processor;

import dev.nexcraft.maegae.internal.json.JsonArray;
import dev.nexcraft.maegae.internal.json.JsonObject;
import dev.nexcraft.maegae.internal.json.JsonString;
import dev.nexcraft.maegae.processor.PipelineHarness.Compilation;
import dev.nexcraft.maegae.protocol.mcp.ToolDefinition;
import dev.nexcraft.maegae.tool.ToolArgumentException;
import dev.nexcraft.maegae.tool.ToolOutput;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

import static dev.nexcraft.maegae.processor.PipelineHarness.IMPORTS;
import static dev.nexcraft.maegae.processor.PipelineHarness.call;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end tests of the annotation processing pipeline: annotated source, real javac with
 * {@code -Xlint:all -Werror}, generated metadata and adapters, and direct Tool method execution.
 */
class ToolPipelineTest {
    private static final Pattern HEX_SUFFIX = Pattern.compile("[0-9a-f]{64}");
    /** Java parameter type to the JSON Schema type that the metadata must declare for it. */
    private static final Map<String, String> SCHEMA_TYPES = new LinkedHashMap<>();
    /** Types that accept null, so an argument of that type can be optional. */
    private static final List<String> NULLABLE = List.of("String", "Boolean", "Byte", "Short", "Integer", "Long",
            "Float", "Double", "BigInteger", "BigDecimal");

    static {
        SCHEMA_TYPES.put("String", "string");
        SCHEMA_TYPES.put("boolean", "boolean");
        SCHEMA_TYPES.put("Boolean", "boolean");
        for (final String type : List.of("byte", "Byte", "short", "Short", "int", "Integer", "long", "Long",
                "BigInteger")) {
            SCHEMA_TYPES.put(type, "integer");
        }
        for (final String type : List.of("float", "Float", "double", "Double", "BigDecimal")) {
            SCHEMA_TYPES.put(type, "number");
        }
    }

    private static final Map<String, List<String>> VALID_VALUES = Map.of(
            "integer", List.of("1", "1.0", "-3", "1e0"),
            "number", List.of("1.5", "2", "1e2"),
            "string", List.of("\"text\""),
            "boolean", List.of("true", "false"));
    private static final Map<String, List<String>> WRONG_TYPE_VALUES = Map.of(
            "integer", List.of("1.5", "\"1\"", "true", "[1]"),
            "number", List.of("\"1\"", "true", "{}"),
            "string", List.of("1", "true", "{}"),
            "boolean", List.of("\"true\"", "1", "[]"));

    private final PipelineHarness harness = new PipelineHarness();

    @AfterEach
    void closeHarness() throws Exception {
        harness.close();
    }

    // A. Metadata and invoker consistency

    @Test
    void metadataAndInvokersAgreeOnNamesTypesAndRequiredArguments(@TempDir final Path output) throws Exception {
        final StringBuilder source = new StringBuilder(IMPORTS).append("public class Pipeline {\n");
        for (final String type : SCHEMA_TYPES.keySet()) {
            source.append("  @Tool public String required_").append(type).append('(').append(type)
                    .append(" value) { return String.valueOf(value); }\n");
        }
        for (final String type : NULLABLE) {
            source.append("  @Tool public String optional_").append(type).append("(@Arg(required = false) ")
                    .append(type).append(" value) { return String.valueOf(value); }\n");
        }
        source.append("""
                  @Tool(name = "sum.values", description = "Adds two numbers.")
                  public String sum(@Arg(name = "left", description = "Left operand.") int a, @Arg(name = "right") Long b,
                                    @Arg(name = "note", description = "Free text.", required = false) String c) {
                      return a + "," + b + "," + c;
                  }
                }
                """);
        final Compilation compilation = harness.compile(output, Map.of("Pipeline.java", source.toString()));
        assertTrue(compilation.success(), compilation.diagnostics().toString());
        final var loaded = harness.load(compilation, "Pipeline");

        final List<String> names = loaded.tools().stream().map(ToolDefinition::name).toList();
        assertEquals(SCHEMA_TYPES.size() + NULLABLE.size() + 1, names.size());
        assertEquals(names, List.copyOf(loaded.invokers().keySet()));
        assertEquals(List.copyOf(new TreeSet<>(names)), names);

        for (final ToolDefinition definition : loaded.tools()) {
            final JsonObject schema = PipelineHarness.parse(definition.inputSchemaJson());
            final JsonObject properties = (JsonObject) schema.values().get("properties");
            final Set<String> required = new TreeSet<>();
            for (final var value : ((JsonArray) schema.values().get("required")).values()) {
                required.add(((JsonString) value).value());
            }
            final Map<String, String> propertyTypes = new LinkedHashMap<>();
            properties.values().forEach((name, property) -> propertyTypes.put(name,
                    ((JsonString) ((JsonObject) property).values().get("type")).value()));
            assertFalse(propertyTypes.isEmpty(), definition.name());
            if (definition.name().startsWith("required_") || definition.name().startsWith("optional_")) {
                final String type = definition.name().substring(definition.name().indexOf('_') + 1);
                assertEquals(Map.of("value", SCHEMA_TYPES.get(type)), propertyTypes, definition.name());
                assertEquals(definition.name().startsWith("required_") ? Set.of("value") : Set.<String>of(),
                        required, definition.name());
            }
            assertInvokerMatchesSchema(loaded.invokers(), definition.name(), propertyTypes, required);
        }

        final ToolDefinition sum = loaded.tools().stream().filter(d -> d.name().equals("sum.values")).findFirst()
                .orElseThrow();
        assertEquals(java.util.Optional.of("Adds two numbers."), sum.description());
        final JsonObject sumSchema = PipelineHarness.parse(sum.inputSchemaJson());
        final JsonObject sumProperties = (JsonObject) sumSchema.values().get("properties");
        assertEquals(List.of("left", "right", "note"), List.copyOf(sumProperties.values().keySet()));
        assertEquals(new JsonString("Left operand."),
                ((JsonObject) sumProperties.values().get("left")).values().get("description"));
        assertEquals(new JsonString("Free text."),
                ((JsonObject) sumProperties.values().get("note")).values().get("description"));
        assertEquals(ToolOutput.text("1,2,null"), call(loaded.invokers(), "sum.values", "{\"left\":1,\"right\":2}"));
        assertEquals(ToolOutput.text("1,2,hi"),
                call(loaded.invokers(), "sum.values", "{\"left\":1,\"right\":2,\"note\":\"hi\"}"));
    }

    /**
     * Drives the invoker only from the generated schema: valid values of the schema type pass,
     * values of another JSON type fail, and omitting a property fails if and only if it is required.
     */
    private static void assertInvokerMatchesSchema(final Map<String, dev.nexcraft.maegae.tool.ToolInvoker> invokers,
                                                   final String tool, final Map<String, String> propertyTypes,
                                                   final Set<String> required) throws Exception {
        final Map<String, String> valid = new LinkedHashMap<>();
        propertyTypes.forEach((name, type) -> valid.put(name, VALID_VALUES.get(type).getFirst()));
        assertDoesNotThrow(() -> call(invokers, tool, json(valid)), tool);
        for (final var property : propertyTypes.entrySet()) {
            for (final String value : VALID_VALUES.get(property.getValue())) {
                final Map<String, String> arguments = new LinkedHashMap<>(valid);
                arguments.put(property.getKey(), value);
                assertDoesNotThrow(() -> call(invokers, tool, json(arguments)), tool + " " + arguments);
            }
            for (final String value : WRONG_TYPE_VALUES.get(property.getValue())) {
                final Map<String, String> arguments = new LinkedHashMap<>(valid);
                arguments.put(property.getKey(), value);
                final var exception = assertThrows(ToolArgumentException.class,
                        () -> call(invokers, tool, json(arguments)), tool + " " + arguments);
                assertEquals(property.getKey(), exception.argumentName(), tool + " " + arguments);
            }
            final Map<String, String> without = new LinkedHashMap<>(valid);
            without.remove(property.getKey());
            if (required.contains(property.getKey())) {
                final var exception = assertThrows(ToolArgumentException.class,
                        () -> call(invokers, tool, json(without)), tool + " without " + property.getKey());
                assertEquals(property.getKey(), exception.argumentName());
            } else {
                assertDoesNotThrow(() -> call(invokers, tool, json(without)),
                        tool + " without optional " + property.getKey());
            }
        }
    }

    private static String json(final Map<String, String> members) {
        final StringBuilder result = new StringBuilder("{");
        members.forEach((name, value) -> result.append(result.length() > 1 ? "," : "").append('"').append(name)
                .append("\":").append(value));
        return result.append('}').toString();
    }

    // B. Multiple providers and naming

    @Test
    void generatesIndependentAdaptersForProvidersOfEveryShapeAndRepeatsByteForByte(@TempDir final Path output)
            throws Exception {
        final Map<String, String> sources = new LinkedHashMap<>();
        final Map<String, String> providers = new LinkedHashMap<>();
        providers.put("pub.PublicTools", "package pub;\n" + IMPORTS + "public class PublicTools {\n%s}");
        providers.put("pkg.Hidden", "package pkg;\n" + IMPORTS + "class Hidden {\n%s}");
        providers.put("nest.Outer$Inner", "package nest;\n" + IMPORTS
                + "public class Outer { public static class Inner {\n%s} }");
        providers.put("Root", IMPORTS + "public class Root {\n%s}");
        providers.put("a.Tools", "package a;\n" + IMPORTS + "public class Tools {\n%s}");
        providers.put("b.Tools", "package b;\n" + IMPORTS + "public class Tools {\n%s}");
        providers.forEach((binaryName, template) -> sources.put(binaryName.replace('.', '/').split("\\$")[0]
                + ".java", template.formatted("  @Tool public String who() { return \"" + binaryName + "\"; }\n")));

        final Compilation first = harness.compile(Files.createDirectory(output.resolve("first")), sources);
        assertTrue(first.success(), first.diagnostics().toString());

        final List<String> expectedFiles = new ArrayList<>();
        final Set<String> suffixes = new TreeSet<>();
        for (final String binaryName : providers.keySet()) {
            final String suffix = new GeneratedNames().suffix(binaryName);
            assertTrue(HEX_SUFFIX.matcher(suffix).matches(), suffix);
            suffixes.add(suffix);
            final int separator = binaryName.lastIndexOf('.');
            final String directory = separator < 0 ? "" : binaryName.substring(0, separator + 1).replace('.', '/');
            expectedFiles.add(directory + "MaegaeToolMetadata_" + suffix + ".java");
            expectedFiles.add(directory + "MaegaeToolInvokers_" + suffix + ".java");
        }
        assertEquals(providers.size(), suffixes.size());
        assertEquals(expectedFiles.stream().sorted().toList(), first.generatedFiles());
        assertEquals(1, first.generatedFiles().stream().map(file -> file.substring(file.lastIndexOf('/') + 1)
                .length()).distinct().count(), "Generated file names have one fixed length.");

        // The same Tool name "who" exists in every provider. Generation does not compare providers.
        for (final String binaryName : providers.keySet()) {
            final var loaded = harness.load(first, binaryName);
            assertEquals(List.of("who"), loaded.tools().stream().map(ToolDefinition::name).toList());
            assertEquals(ToolOutput.text(binaryName), call(loaded.invokers(), "who", "{}"), binaryName);
        }

        final Compilation second = harness.compile(Files.createDirectory(output.resolve("second")), sources);
        assertTrue(second.success(), second.diagnostics().toString());
        assertEquals(first.generatedContents(), second.generatedContents());
    }

    // C. Generics and warnings

    @Test
    void genericProvidersAcceptAnyParameterizationWithoutWarnings(@TempDir final Path output) throws Exception {
        final String boxSuffix = new GeneratedNames().suffix("gen.Box");
        final String pairSuffix = new GeneratedNames().suffix("gen.Pair");
        final Map<String, String> sources = new LinkedHashMap<>();
        sources.put("gen/Box.java", "package gen;\n" + IMPORTS
                + "public class Box<T extends Number> { @Tool public String boxed(int n) { return \"box\" + n; } }");
        sources.put("gen/Pair.java", "package gen;\n" + IMPORTS + """
                public class Pair<A, B extends Comparable<B>> {
                    @Tool public String pair(String text) { return "pair" + text; }
                }
                """);
        // Compiled with -Xlint:all -Werror. A raw or unchecked adapter signature would fail here.
        sources.put("gen/Use.java", "package gen;\n" + """
                public class Use {
                    static void use() {
                        MaegaeToolInvokers_%s.invokers(new Box<Integer>());
                        MaegaeToolInvokers_%s.invokers(new Box<Double>());
                        final Box<?> unknown = new Box<Long>();
                        MaegaeToolInvokers_%s.invokers(unknown);
                        MaegaeToolInvokers_%s.invokers(new Pair<String, String>());
                    }
                }
                """.formatted(boxSuffix, boxSuffix, boxSuffix, pairSuffix));
        final Compilation compilation = harness.compile(output, sources);
        assertTrue(compilation.success(), compilation.diagnostics().toString());
        assertEquals(List.of(), compilation.diagnostics().stream()
                .filter(d -> d.getKind() != Diagnostic.Kind.NOTE).toList());

        final var box = harness.load(compilation, "gen.Box");
        assertEquals("gen.Box<?>", box.adapter().getMethod("invokers", box.providerClass())
                .getGenericParameterTypes()[0].getTypeName());
        assertEquals(ToolOutput.text("box4"), call(box.invokers(), "boxed", "{\"n\":4}"));
        final var pair = harness.load(compilation, "gen.Pair");
        assertEquals("gen.Pair<?, ?>", pair.adapter().getMethod("invokers", pair.providerClass())
                .getGenericParameterTypes()[0].getTypeName());
        assertEquals(ToolOutput.text("pairx"), call(pair.invokers(), "pair", "{\"text\":\"x\"}"));
    }

    // D. Failure behavior

    /**
     * Current behavior, documented by this test: provider declarations are independent.
     * A provider without errors still gets both generated files when another provider is invalid.
     * The invalid provider gets no file. javac reports the error and writes no class file.
     */
    @ParameterizedTest
    @ValueSource(strings = {"@Tool public Object bad() { return null; }", "@Tool String notPublic() { return \"\"; }"})
    void anInvalidProviderGeneratesNothingAndDoesNotBlockTheValidProviderFiles(final String badMember,
                                                                              @TempDir final Path output)
            throws Exception {
        final Map<String, String> sources = new LinkedHashMap<>();
        sources.put("Good.java", IMPORTS + "public class Good { @Tool public String ok() { return \"ok\"; } }");
        sources.put("Bad.java", IMPORTS + "public class Bad { " + badMember + " }");
        final Compilation compilation = harness.compile(output, sources);
        assertFalse(compilation.success());
        assertEquals(1, compilation.errors().size(), compilation.diagnostics().toString());
        final String goodSuffix = new GeneratedNames().suffix("Good");
        assertEquals(List.of("MaegaeToolInvokers_" + goodSuffix + ".java", "MaegaeToolMetadata_" + goodSuffix + ".java"),
                compilation.generatedFiles());
        assertEquals(0, compilation.classFileCount());
    }

    @Test
    void diagnosticsKeepTheSourceLineAndNameTheToolArgumentAndType(@TempDir final Path output) throws Exception {
        final String source = IMPORTS + """
                public class Diagnostics {
                    @Tool
                    public String parameter(
                            String fine,
                            java.util.List<String> items) { return ""; }

                    @Tool
                    public String optionalPrimitive(
                            @Arg(required = false)
                            int count) { return ""; }

                    @Tool
                    public java.util.Map<String, Integer>
                            result() { return null; }
                }
                """;
        final Compilation compilation = harness.compile(output, Map.of("Diagnostics.java", source));
        assertFalse(compilation.success());
        final Map<Integer, String> messages = new TreeMap<>();
        for (final var error : compilation.errors()) {
            assertTrue(error.getSource().getName().endsWith("Diagnostics.java"), error.toString());
            messages.put((int) error.getLineNumber(), error.getMessage(Locale.ROOT));
        }
        assertEquals(3, messages.size(), compilation.diagnostics().toString());
        assertEquals(List.of(lineOf(source, "java.util.List<String> items"), lineOf(source, "int count"),
                lineOf(source, "result()")), List.copyOf(messages.keySet()));
        final List<String> texts = List.copyOf(messages.values());
        assertEquals("Unsupported MCP Tool argument type 'java.util.List<java.lang.String>' for parameter 'items'"
                + " in Tool 'parameter'.", texts.get(0));
        assertTrue(texts.get(1).contains("'count' in Tool 'optionalPrimitive'") && texts.get(1).contains("'int'"),
                texts.get(1));
        assertTrue(texts.get(2).contains("'java.util.Map<java.lang.String,java.lang.Integer>' in Tool 'result'"),
                texts.get(2));
    }

    private static int lineOf(final String source, final String fragment) {
        final String[] lines = source.split("\n", -1);
        for (int index = 0; index < lines.length; index++) {
            if (lines[index].contains(fragment)) {
                return index + 1;
            }
        }
        throw new AssertionError("Missing fragment " + fragment);
    }

    // E. Rounds and originating elements

    /** Writes one provider source in round one. Maegae must process it in the next round, once. */
    private static final class SourceGeneratingProcessor extends AbstractProcessor {
        private boolean generated;

        @Override
        public Set<String> getSupportedAnnotationTypes() {
            return Set.of("*");
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported();
        }

        @Override
        public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
            if (!generated) {
                generated = true;
                try (final Writer writer = processingEnv.getFiler().createSourceFile("Late").openWriter()) {
                    writer.write(IMPORTS + "public class Late { @Tool public String late() { return \"late\"; } }");
                } catch (IOException exception) {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, exception.toString());
                }
            }
            return false;
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void processesProvidersFromEarlierAndLaterRoundsExactlyOnce(final boolean generatorFirst,
                                                               @TempDir final Path output) throws Exception {
        final Processor generator = new SourceGeneratingProcessor();
        final Processor maegae = new McpToolProcessor();
        final Compilation compilation = harness.compile(output, Map.of("Early.java", IMPORTS
                        + "public class Early { @Tool public String early() { return \"early\"; } }"),
                generatorFirst ? List.of(generator, maegae) : List.of(maegae, generator));
        assertTrue(compilation.success(), compilation.diagnostics().toString());
        final List<String> expected = new ArrayList<>();
        for (final String provider : List.of("Early", "Late")) {
            final String suffix = new GeneratedNames().suffix(provider);
            expected.add("MaegaeToolInvokers_" + suffix + ".java");
            expected.add("MaegaeToolMetadata_" + suffix + ".java");
        }
        expected.add("Late.java");
        assertEquals(expected.stream().sorted().toList(), compilation.generatedFiles());
        assertEquals(ToolOutput.text("early"), call(harness.load(compilation, "Early").invokers(), "early", "{}"));
        assertEquals(ToolOutput.text("late"), call(harness.load(compilation, "Late").invokers(), "late", "{}"));
    }

    /** Runs the real processor with a Filer that records the originating elements of every source file. */
    private static final class RecordingProcessor implements Processor {
        private final Processor delegate = new McpToolProcessor();
        final Map<String, List<String>> origins = new LinkedHashMap<>();

        @Override
        public void init(final ProcessingEnvironment environment) {
            delegate.init(new RecordingEnvironment(environment, origins));
        }

        @Override
        public Set<String> getSupportedOptions() {
            return delegate.getSupportedOptions();
        }

        @Override
        public Set<String> getSupportedAnnotationTypes() {
            return delegate.getSupportedAnnotationTypes();
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return delegate.getSupportedSourceVersion();
        }

        @Override
        public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
            return delegate.process(annotations, roundEnv);
        }

        @Override
        public Iterable<? extends javax.annotation.processing.Completion> getCompletions(
                final Element element, final AnnotationMirror annotation, final ExecutableElement member,
                final String userText) {
            return delegate.getCompletions(element, annotation, member, userText);
        }
    }

    private record RecordingEnvironment(ProcessingEnvironment delegate, Map<String, List<String>> origins)
            implements ProcessingEnvironment {
        @Override
        public Map<String, String> getOptions() {
            return delegate.getOptions();
        }

        @Override
        public Messager getMessager() {
            return delegate.getMessager();
        }

        @Override
        public Filer getFiler() {
            final Filer filer = delegate.getFiler();
            return new Filer() {
                @Override
                public JavaFileObject createSourceFile(final CharSequence name, final Element... originatingElements)
                        throws IOException {
                    origins.put(name.toString(), java.util.Arrays.stream(originatingElements)
                            .map(element -> element.getKind() + ":" + element).toList());
                    return filer.createSourceFile(name, originatingElements);
                }

                @Override
                public JavaFileObject createClassFile(final CharSequence name, final Element... originatingElements)
                        throws IOException {
                    return filer.createClassFile(name, originatingElements);
                }

                @Override
                public FileObject createResource(final JavaFileManager.Location location, final CharSequence moduleAndPkg,
                                                 final CharSequence relativeName, final Element... originatingElements)
                        throws IOException {
                    return filer.createResource(location, moduleAndPkg, relativeName, originatingElements);
                }

                @Override
                public FileObject getResource(final JavaFileManager.Location location, final CharSequence moduleAndPkg,
                                              final CharSequence relativeName) throws IOException {
                    return filer.getResource(location, moduleAndPkg, relativeName);
                }
            };
        }

        @Override
        public Elements getElementUtils() {
            return delegate.getElementUtils();
        }

        @Override
        public Types getTypeUtils() {
            return delegate.getTypeUtils();
        }

        @Override
        public SourceVersion getSourceVersion() {
            return delegate.getSourceVersion();
        }

        @Override
        public Locale getLocale() {
            return delegate.getLocale();
        }
    }

    @Test
    void bothGeneratedFilesNameTheProviderAndEveryToolMethodAsOriginatingElements(@TempDir final Path output)
            throws Exception {
        final var recorder = new RecordingProcessor();
        final Compilation compilation = harness.compile(output, Map.of("example/Tools.java", "package example;\n"
                        + IMPORTS + """
                        public class Tools {
                            @Tool public String beta(int value) { return ""; }
                            @Tool public String alpha() { return ""; }
                        }
                        """), List.of(recorder));
        assertTrue(compilation.success(), compilation.diagnostics().toString());
        final String suffix = new GeneratedNames().suffix("example.Tools");
        final List<String> expected = List.of("CLASS:example.Tools", "METHOD:alpha()", "METHOD:beta(int)");
        assertEquals(Map.of("example.MaegaeToolMetadata_" + suffix, expected,
                "example.MaegaeToolInvokers_" + suffix, expected), recorder.origins);
    }

    // F. Null returns of boxed types

    @ParameterizedTest
    @ValueSource(strings = {"Boolean", "Byte", "Short", "Long", "Float", "Double", "BigInteger"})
    void aNullBoxedResultBecomesEmptyOutput(final String type, @TempDir final Path output) throws Exception {
        final Compilation compilation = harness.compile(output, Map.of("Nulls.java", IMPORTS + "public class Nulls {"
                + " @Tool public " + type + " nothing() { return null; } }"));
        assertTrue(compilation.success(), compilation.diagnostics().toString());
        final ToolOutput result = call(harness.load(compilation, "Nulls").invokers(), "nothing", "{}");
        assertEquals(ToolOutput.empty(), result);
        assertNotEquals(ToolOutput.text("null"), result);
    }
}
