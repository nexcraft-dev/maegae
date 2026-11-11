package dev.nexcraft.maegae.processor;

import dev.nexcraft.maegae.annotation.Tool;
import dev.nexcraft.maegae.internal.json.JacksonJsonCodec;
import dev.nexcraft.maegae.internal.json.JsonObject;
import dev.nexcraft.maegae.internal.tool.JsonToolArguments;
import dev.nexcraft.maegae.tool.ToolArgumentException;
import dev.nexcraft.maegae.tool.ToolInvoker;
import dev.nexcraft.maegae.tool.ToolOutput;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Compiles providers with the real processor, then runs the generated invocation adapters. */
class ToolInvokerGenerationTest {
    private static final String IMPORTS = "import dev.nexcraft.maegae.annotation.*;\nimport java.math.*;\n";
    private static final JacksonJsonCodec CODEC = new JacksonJsonCodec();
    private final List<URLClassLoader> loaders = new ArrayList<>();

    @AfterEach
    void closeLoaders() throws Exception {
        for (final URLClassLoader loader : loaders) {
            loader.close();
        }
    }

    @Test
    void runsTheMatchingMethodWithArgumentsMappedByName(@TempDir final Path output) throws Exception {
        final var invokers = load(output, IMPORTS + """
                public class ExampleTools implements java.util.function.Supplier<Object> {
                    private final java.util.List<String> calls = new java.util.ArrayList<>();
                    public Object get() { return calls; }
                    @Tool public String greet(String name, int times) {
                        calls.add("greet");
                        return name.repeat(times);
                    }
                    @Tool public int subtract(@Arg(name = "minuend") int a, @Arg(name = "subtrahend") int b) {
                        calls.add("subtract");
                        return a - b;
                    }
                    @Tool(name = "unicode 이름 \\"quoted\\"") public String named() { return "named"; }
                }
                """, "ExampleTools");
        assertEquals(ToolOutput.text("ababab"), call(invokers, "greet", "{\"times\":3,\"name\":\"ab\"}"));
        assertEquals(ToolOutput.text("7"), call(invokers, "subtract", "{\"subtrahend\":3,\"minuend\":10}"));
        assertEquals(ToolOutput.text("named"), call(invokers, "unicode 이름 \"quoted\"", "{}"));
        assertEquals(List.of("greet", "subtract"), ((Supplier<?>) invokers.provider()).get());
        // Unknown arguments are ignored.
        assertEquals(ToolOutput.text("x"), call(invokers, "greet", "{\"name\":\"x\",\"times\":1,\"extra\":[1,2]}"));
    }

    @Test
    void requiredArgumentsMustBePresentAndNonNull(@TempDir final Path output) throws Exception {
        final var invokers = load(output, IMPORTS + """
                public class ExampleTools {
                    @Tool public String echo(String text, Integer boxed) { return text + boxed; }
                }
                """, "ExampleTools");
        assertEquals(ToolOutput.text("a1"), call(invokers, "echo", "{\"text\":\"a\",\"boxed\":1}"));
        final Map<String, String> expectedNames = new LinkedHashMap<>();
        expectedNames.put("{\"boxed\":1}", "text");
        expectedNames.put("{\"text\":null,\"boxed\":1}", "text");
        expectedNames.put("{\"text\":\"a\"}", "boxed");
        expectedNames.put("{\"text\":\"a\",\"boxed\":null}", "boxed");
        for (final var entry : expectedNames.entrySet()) {
            final var exception = assertThrows(ToolArgumentException.class,
                    () -> call(invokers, "echo", entry.getKey()), entry.getKey());
            assertEquals(entry.getValue(), exception.argumentName(), entry.getKey());
        }
        final var wrongType = assertThrows(ToolArgumentException.class,
                () -> call(invokers, "echo", "{\"text\":1,\"boxed\":1}"));
        assertEquals("text", wrongType.argumentName());
    }

    @Test
    void optionalNullableArgumentsReceiveNullWhenMissingOrNull(@TempDir final Path output) throws Exception {
        final var invokers = load(output, IMPORTS + """
                public class ExampleTools {
                    @Tool public String optional(
                            @Arg(required = false) String text, @Arg(required = false) Integer number,
                            @Arg(required = false) BigDecimal decimal, @Arg(required = false) Boolean flag) {
                        return text + "," + number + "," + decimal + "," + flag;
                    }
                }
                """, "ExampleTools");
        assertEquals(ToolOutput.text("null,null,null,null"), call(invokers, "optional", "{}"));
        assertEquals(ToolOutput.text("null,null,null,null"), call(invokers, "optional",
                "{\"text\":null,\"number\":null,\"decimal\":null,\"flag\":null}"));
        assertEquals(ToolOutput.text("x,5,1.50,false"), call(invokers, "optional",
                "{\"text\":\"x\",\"number\":5,\"decimal\":1.50,\"flag\":false}"));
        // A supplied value of the wrong type is an error, not a silent null.
        final var exception = assertThrows(ToolArgumentException.class,
                () -> call(invokers, "optional", "{\"number\":\"5\"}"));
        assertEquals("number", exception.argumentName());
    }

    @Test
    void rejectsOptionalPrimitiveParameterAtCompileTime(@TempDir final Path output) throws Exception {
        final var result = compile(output, Map.of("ExampleTools.java", IMPORTS
                + "class ExampleTools { @Tool public void call(@Arg(required = false) int count) {} }"));
        assertFalse(result.success());
        final var errors = errors(result);
        assertEquals(1, errors.size(), result.diagnostics().toString());
        assertTrue(errors.getFirst().getMessage(Locale.ROOT).contains("'count' in Tool 'call' must not use primitive"
                + " type 'int'"), errors.getFirst().toString());
        assertTrue(errors.getFirst().getLineNumber() > 0);
        assertNoGeneratedFiles(result);
    }

    @Test
    void convertsEverySupportedReturnType(@TempDir final Path output) throws Exception {
        final var invokers = load(output, IMPORTS + """
                public class ExampleTools {
                    @Tool public void nothing() {}
                    @Tool public String text() { return "text"; }
                    @Tool public String emptyText() { return ""; }
                    @Tool public String nullText() { return null; }
                    @Tool public boolean bool() { return true; }
                    @Tool public byte b() { return -8; }
                    @Tool public short s() { return 300; }
                    @Tool public int i() { return -70000; }
                    @Tool public long l() { return 5000000000L; }
                    @Tool public float f() { return 1.5f; }
                    @Tool public double d() { return -2.25; }
                    @Tool public Boolean boxedBool() { return false; }
                    @Tool public Byte boxedByte() { return 8; }
                    @Tool public Short boxedShort() { return 9; }
                    @Tool public Integer boxedInt() { return 10; }
                    @Tool public Long boxedLong() { return 11L; }
                    @Tool public Float boxedFloat() { return 0.5f; }
                    @Tool public Double boxedDouble() { return Double.NaN; }
                    @Tool public Integer nullInt() { return null; }
                    @Tool public BigInteger bigInt() { return new BigInteger("123456789012345678901234567890"); }
                    @Tool public BigDecimal bigDec() { return new BigDecimal("1E+2"); }
                    @Tool public BigDecimal nullDec() { return null; }
                }
                """, "ExampleTools");
        final Map<String, Optional<String>> expected = new LinkedHashMap<>();
        expected.put("nothing", Optional.empty());
        expected.put("text", Optional.of("text"));
        expected.put("emptyText", Optional.of(""));
        expected.put("nullText", Optional.empty());
        expected.put("bool", Optional.of("true"));
        expected.put("b", Optional.of("-8"));
        expected.put("s", Optional.of("300"));
        expected.put("i", Optional.of("-70000"));
        expected.put("l", Optional.of("5000000000"));
        expected.put("f", Optional.of("1.5"));
        expected.put("d", Optional.of("-2.25"));
        expected.put("boxedBool", Optional.of("false"));
        expected.put("boxedByte", Optional.of("8"));
        expected.put("boxedShort", Optional.of("9"));
        expected.put("boxedInt", Optional.of("10"));
        expected.put("boxedLong", Optional.of("11"));
        expected.put("boxedFloat", Optional.of("0.5"));
        expected.put("boxedDouble", Optional.of("NaN"));
        expected.put("nullInt", Optional.empty());
        expected.put("bigInt", Optional.of("123456789012345678901234567890"));
        expected.put("bigDec", Optional.of("1E+2"));
        expected.put("nullDec", Optional.empty());
        assertEquals(List.copyOf(new java.util.TreeSet<>(expected.keySet())), List.copyOf(invokers.map().keySet()));
        for (final var entry : expected.entrySet()) {
            assertEquals(entry.getValue(), call(invokers, entry.getKey(), "{}").text(), entry.getKey());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"Object", "java.util.List<String>", "char", "Character", "int[]", "Void",
            "java.util.Optional<String>", "Thread.State"})
    void rejectsUnsupportedReturnTypesAtCompileTime(final String type, @TempDir final Path output) throws Exception {
        final var result = compile(output, Map.of("ExampleTools.java", IMPORTS
                + "class ExampleTools { @Tool public " + type + " call() { return null; } }"));
        assertFalse(result.success());
        final var errors = errors(result);
        assertEquals(1, errors.size(), result.diagnostics().toString());
        final String message = errors.getFirst().getMessage(Locale.ROOT);
        assertTrue(message.contains("Unsupported MCP Tool return type") && message.contains("in Tool 'call'")
                && message.contains("Supported return types"), message);
        assertTrue(errors.getFirst().getLineNumber() > 0);
        assertNoGeneratedFiles(result);
    }

    @Test
    void propagatesToolMethodExceptionsUnchanged(@TempDir final Path output) throws Exception {
        final var invokers = load(output, IMPORTS + """
                public class ExampleTools implements java.util.function.Supplier<Object> {
                    static class Checked extends Exception { Checked(String message) { super(message); } }
                    private final Exception failure = new Checked("checked failure");
                    private final RuntimeException runtimeFailure = new IllegalStateException("runtime failure");
                    private Object last;
                    public Object get() { return last; }
                    @Tool public void checked() throws Exception { last = failure; throw failure; }
                    @Tool public String unchecked() { last = runtimeFailure; throw runtimeFailure; }
                }
                """, "ExampleTools");
        final Supplier<?> supplier = (Supplier<?>) invokers.provider();
        final Exception checked = assertThrows(Exception.class, () -> call(invokers, "checked", "{}"));
        assertEquals(supplier.get(), checked);
        final Exception unchecked = assertThrows(IllegalStateException.class, () -> call(invokers, "unchecked", "{}"));
        assertEquals(supplier.get(), unchecked);
        assertEquals(0, unchecked.getSuppressed().length);
        assertNull(unchecked.getCause());
    }

    @Test
    void returnsImmutableMapInToolNameOrderAndRejectsNullProvider(@TempDir final Path output) throws Exception {
        final var invokers = load(output, IMPORTS + """
                public class ExampleTools {
                    @Tool(name = "zeta") public String first() { return "first"; }
                    @Tool(name = "alpha") public String second() { return "second"; }
                    @Tool(name = "mid") public String third() { return "third"; }
                }
                """, "ExampleTools");
        assertEquals(List.of("alpha", "mid", "zeta"), List.copyOf(invokers.map().keySet()));
        assertThrows(UnsupportedOperationException.class, () -> invokers.map().remove("alpha"));
        assertThrows(UnsupportedOperationException.class, () -> invokers.map().put("new", args -> ToolOutput.empty()));
        assertEquals(ToolOutput.text("first"), call(invokers, "zeta", "{}"));
        final var failure = assertThrows(java.lang.reflect.InvocationTargetException.class,
                () -> invokers.adapter().getMethod("invokers", invokers.providerClass()).invoke(null, (Object) null));
        assertTrue(failure.getCause() instanceof NullPointerException);
    }

    @Test
    void overloadedJavaMethodsWithDifferentToolNamesGetDistinctAdapters(@TempDir final Path output) throws Exception {
        final var invokers = load(output, IMPORTS + """
                public class ExampleTools {
                    @Tool(name = "by.int") public String format(int value) { return "int " + value; }
                    @Tool(name = "by.text") public String format(String value) { return "text " + value; }
                    @Tool(name = "third") public String format(boolean value) { return "boolean " + value; }
                }
                """, "ExampleTools");
        assertEquals(ToolOutput.text("int 5"), call(invokers, "by.int", "{\"value\":5}"));
        assertEquals(ToolOutput.text("text a"), call(invokers, "by.text", "{\"value\":\"a\"}"));
        assertEquals(ToolOutput.text("boolean true"), call(invokers, "third", "{\"value\":true}"));
    }

    @Test
    void supportsNestedAndGenericProviders(@TempDir final Path output) throws Exception {
        final var source = "package example;\n" + IMPORTS + """
                class Outer {
                    static class Inner { @Tool public String inner() { return "inner"; } }
                    static class Hidden { @Tool public String hidden() { return "hidden"; } }
                }
                class Box<T> { @Tool public String boxed() { return "boxed"; } }
                """;
        final var inner = load(output.resolve("inner"), source, "example.Outer$Inner");
        assertEquals(ToolOutput.text("inner"), call(inner, "inner", "{}"));
        final var hidden = load(output.resolve("hidden"), source, "example.Outer$Hidden");
        assertEquals(ToolOutput.text("hidden"), call(hidden, "hidden", "{}"));
        final var box = load(output.resolve("box"), source, "example.Box");
        assertEquals(ToolOutput.text("boxed"), call(box, "boxed", "{}"));
    }

    @Test
    void separatesProvidersWithTheSameSimpleNameInDifferentPackages(@TempDir final Path output) throws Exception {
        final Map<String, String> sources = new LinkedHashMap<>();
        sources.put("a/Tools.java", "package a;\n" + IMPORTS
                + "public class Tools { @Tool public String who() { return \"a\"; } }");
        sources.put("b/Tools.java", "package b;\n" + IMPORTS
                + "public class Tools { @Tool public String who() { return \"b\"; } }");
        final var result = compile(output, sources);
        assertTrue(result.success(), result.diagnostics().toString());
        assertEquals(ToolOutput.text("a"), call(open(result, "a.Tools"), "who", "{}"));
        assertEquals(ToolOutput.text("b"), call(open(result, "b.Tools"), "who", "{}"));
    }

    @Test
    void producesIdenticalSourcesOnRepeatedCompilation(@TempDir final Path output) throws Exception {
        final Map<String, String> sources = Map.of("ExampleTools.java", IMPORTS + """
                class ExampleTools {
                    @Tool(name = "b") public String format(@Arg(required = false) String text, int count) { return ""; }
                    @Tool(name = "a") public int format(boolean flag) { return 1; }
                    @Tool(name = "c") public void format(BigDecimal value) {}
                }
                """);
        final var first = compile(Files.createDirectory(output.resolve("first")), sources);
        final var second = compile(Files.createDirectory(output.resolve("second")), sources);
        assertTrue(first.success(), first.diagnostics().toString());
        assertTrue(second.success(), second.diagnostics().toString());
        try (final var files = Files.walk(first.generated())) {
            final var generated = files.filter(Files::isRegularFile).sorted().toList();
            assertEquals(3, generated.size());
            for (final Path file : generated) {
                assertEquals(Files.readString(file),
                        Files.readString(second.generated().resolve(first.generated().relativize(file))));
            }
        }
    }

    @Test
    void generatedSourceDoesNotUseReflectionOrCoreInternals(@TempDir final Path output) throws Exception {
        final var result = compile(output, Map.of("ExampleTools.java", IMPORTS
                + "class ExampleTools { @Tool public int add(int a, @Arg(required = false) Integer b) { return a; } }"));
        assertTrue(result.success(), result.diagnostics().toString());
        final String invokerName = "MaegaeToolInvokers_" + new GeneratedNames().suffix("ExampleTools") + ".java";
        final String source = Files.readString(result.generated().resolve(invokerName));
        assertFalse(Stream.of("java.lang.reflect", "Class.forName", "getMethod", "getDeclared", "setAccessible",
                "internal.json", "jackson", "netty").anyMatch(source::contains), source);
    }

    @Test
    void generatesNeitherFileWhenAnyDeclarationIsInvalid(@TempDir final Path output) throws Exception {
        final var badReturn = compile(Files.createDirectory(output.resolve("return")), Map.of("ExampleTools.java",
                IMPORTS + """
                        class ExampleTools {
                            @Tool public String valid(String text) { return text; }
                            @Tool public Object invalid() { return null; }
                        }
                        """));
        assertFalse(badReturn.success());
        assertNoGeneratedFiles(badReturn);
        final var badArgument = compile(Files.createDirectory(output.resolve("argument")), Map.of("ExampleTools.java",
                IMPORTS + """
                        class ExampleTools {
                            @Tool public String valid(String text) { return text; }
                            @Tool public String invalid(Object value) { return ""; }
                        }
                        """));
        assertFalse(badArgument.success());
        assertNoGeneratedFiles(badArgument);
    }

    @Test
    void reportsCollisionWithAnExistingInvokerClassAndGeneratesNothing(@TempDir final Path output) throws Exception {
        final String invokerName = "MaegaeToolInvokers_" + new GeneratedNames().suffix("ExampleTools");
        final var result = compile(output, Map.of("ExampleTools.java", IMPORTS
                + "class ExampleTools { @Tool public void call() {} }\nclass " + invokerName + " {}\n"));
        assertFalse(result.success());
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.getMessage(Locale.ROOT)
                .contains("Cannot generate Tool invokers '" + invokerName + "'")), result.diagnostics().toString());
        assertNoGeneratedFiles(result);
    }

    private ToolInvokers load(final Path output, final String source, final String providerBinaryName)
            throws Exception {
        Files.createDirectories(output);
        final var result = compile(output, Map.of("ExampleTools.java", source));
        assertTrue(result.success(), result.diagnostics().toString());
        return open(result, providerBinaryName);
    }

    private ToolInvokers open(final Compilation compilation, final String binaryName) throws Exception {
        final var loader = new URLClassLoader(new java.net.URL[]{compilation.classes().toUri().toURL()},
                ToolInvoker.class.getClassLoader());
        loaders.add(loader);
        final int separator = binaryName.lastIndexOf('.');
        final String packageName = separator < 0 ? "" : binaryName.substring(0, separator + 1);
        final Class<?> adapter = loader.loadClass(packageName + "MaegaeToolInvokers_"
                + new GeneratedNames().suffix(binaryName));
        final Class<?> providerClass = loader.loadClass(binaryName);
        final var constructor = providerClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        final Object provider = constructor.newInstance();
        @SuppressWarnings("unchecked") final Map<String, ToolInvoker> map =
                (Map<String, ToolInvoker>) adapter.getMethod("invokers", providerClass).invoke(null, provider);
        return new ToolInvokers(map, provider, adapter, providerClass);
    }

    private static ToolOutput call(final ToolInvokers invokers, final String tool, final String json)
            throws Exception {
        final ToolInvoker invoker = invokers.map().get(tool);
        assertNotNull(invoker, "Missing Tool " + tool + " in " + invokers.map().keySet());
        return invoker.invoke(JsonToolArguments.of((JsonObject) CODEC.parse(json)));
    }

    private static List<Diagnostic<? extends JavaFileObject>> errors(final Compilation compilation) {
        return compilation.diagnostics().stream().filter(d -> d.getKind() == Diagnostic.Kind.ERROR).toList();
    }

    private static void assertNoGeneratedFiles(final Compilation compilation) throws Exception {
        try (final var generated = Files.walk(compilation.generated())) {
            assertEquals(List.of(), generated.filter(Files::isRegularFile).toList());
        }
    }

    private static Compilation compile(final Path output, final Map<String, String> sources) throws Exception {
        final Path classes = Files.createDirectory(output.resolve("classes"));
        final Path generated = Files.createDirectory(output.resolve("generated"));
        final List<Path> files = new ArrayList<>();
        for (final var source : sources.entrySet()) {
            final Path file = output.resolve("src").resolve(source.getKey());
            Files.createDirectories(file.getParent());
            files.add(Files.writeString(file, source.getValue(), StandardCharsets.UTF_8));
        }
        final Path core = Path.of(Tool.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        final var diagnostics = new DiagnosticCollector<JavaFileObject>();
        final var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler);
        try (final var manager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            final var task = compiler.getTask(new StringWriter(), manager, diagnostics, List.of(
                    "--release", "25", "--class-path", core.toString(), "--processor-path",
                    System.getProperty("maegae.processor.jar") + File.pathSeparator + core,
                    "-d", classes.toString(), "-s", generated.toString()), null,
                    manager.getJavaFileObjectsFromPaths(files));
            task.setLocale(Locale.ROOT);
            return new Compilation(task.call(), List.copyOf(diagnostics.getDiagnostics()), classes, generated);
        }
    }

    private record Compilation(boolean success, List<Diagnostic<? extends JavaFileObject>> diagnostics,
                               Path classes, Path generated) { }

    private record ToolInvokers(Map<String, ToolInvoker> map, Object provider, Class<?> adapter,
                                Class<?> providerClass) { }
}
