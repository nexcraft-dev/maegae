package dev.nexcraft.maegae.processor;

import dev.nexcraft.maegae.processor.PipelineHarness.Compilation;
import dev.nexcraft.maegae.protocol.mcp.ToolDefinition;
import dev.nexcraft.maegae.protocol.mcp.ToolProviderBinding;
import dev.nexcraft.maegae.tool.ToolOutput;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import static dev.nexcraft.maegae.processor.PipelineHarness.IMPORTS;
import static dev.nexcraft.maegae.processor.PipelineHarness.call;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests the generated Tool bindings and the service file with the real Java compiler and ServiceLoader. */
class ToolBindingTest {
    private static final String SERVICE_FILE = "META-INF/services/dev.nexcraft.maegae.protocol.mcp.ToolProviderBinding";
    private final PipelineHarness harness = new PipelineHarness();
    private final List<URLClassLoader> loaders = new ArrayList<>();

    @AfterEach
    void close() throws Exception {
        harness.close();
        for (final URLClassLoader loader : loaders) {
            loader.close();
        }
    }

    private URLClassLoader loader(final Compilation compilation) throws Exception {
        final var loader = new URLClassLoader(new URL[]{compilation.classes().toUri().toURL()},
                ToolProviderBinding.class.getClassLoader());
        loaders.add(loader);
        return loader;
    }

    private static String bindingName(final String binaryName) {
        final int separator = binaryName.lastIndexOf('.');
        return (separator < 0 ? "" : binaryName.substring(0, separator + 1)) + "MaegaeToolBinding_"
                + new GeneratedNames().suffix(binaryName);
    }

    private static Map<String, String> single(final String path, final String source) {
        return Map.of(path, source);
    }

    // 1. The binding class

    @Test
    void aBindingDelegatesToTheGeneratedMetadataAndInvokers(@TempDir final Path output) throws Exception {
        final Compilation compilation = harness.compile(output, single("example/Calc.java", "package example;\n"
                + IMPORTS + "public class Calc { @Tool public int add(int a, int b) { return a + b; } }"));
        assertTrue(compilation.success(), compilation.diagnostics().toString());
        final var loader = loader(compilation);
        final Class<?> bindingClass = loader.loadClass(bindingName("example.Calc"));
        final var constructor = bindingClass.getConstructor();
        assertTrue(ToolProviderBinding.class.isAssignableFrom(bindingClass));
        assertEquals(java.lang.reflect.Modifier.PUBLIC | java.lang.reflect.Modifier.FINAL,
                bindingClass.getModifiers());
        final var binding = (ToolProviderBinding) constructor.newInstance();

        final Class<?> providerClass = loader.loadClass("example.Calc");
        final Object provider = providerClass.getConstructor().newInstance();
        final Class<?> metadata = loader.loadClass(bindingName("example.Calc").replace("Binding", "Metadata"));
        assertEquals(providerClass, binding.providerType());
        assertEquals(metadata.getMethod("tools").invoke(null), binding.tools());
        assertEquals(List.of("add"), binding.tools().stream().map(ToolDefinition::name).toList());
        final var invokers = binding.invokers(provider);
        assertEquals(ToolOutput.text("5"), call(invokers, "add", "{\"a\":2,\"b\":3}"));

        assertThrows(NullPointerException.class, () -> binding.invokers(null));
        final var wrong = assertThrows(IllegalArgumentException.class, () -> binding.invokers("not a Calc"));
        assertTrue(wrong.getMessage().contains("example.Calc"), wrong.getMessage());
    }

    // 2. Discovery through ServiceLoader

    @Test
    void serviceLoaderFindsEveryProviderShapeExactlyOnce(@TempDir final Path output) throws Exception {
        final Map<String, String> sources = new LinkedHashMap<>();
        final String tool = "  @Tool public String who() { return \"x\"; }\n";
        sources.put("pub/PublicTools.java", "package pub;\n" + IMPORTS + "public class PublicTools {\n" + tool + "}");
        sources.put("pkg/Hidden.java", "package pkg;\n" + IMPORTS + "class Hidden {\n" + tool + "}");
        sources.put("nest/Outer.java", "package nest;\n" + IMPORTS + "public class Outer {\n"
                + "public static class Inner {\n" + tool + "}\n static class Hidden {\n" + tool + "} }");
        sources.put("Root.java", IMPORTS + "public class Root {\n" + tool + "}");
        sources.put("a/Tools.java", "package a;\n" + IMPORTS + "public class Tools {\n" + tool + "}");
        sources.put("b/Tools.java", "package b;\n" + IMPORTS + "public class Tools {\n" + tool + "}");
        sources.put("gen/Box.java", "package gen;\n" + IMPORTS
                + "public class Box<T extends Number> {\n" + tool + "}");
        final Compilation compilation = harness.compile(output, sources);
        assertTrue(compilation.success(), compilation.diagnostics().toString());

        final List<String> found = new ArrayList<>();
        for (final ToolProviderBinding binding : ServiceLoader.load(ToolProviderBinding.class, loader(compilation))) {
            found.add(binding.providerType().getName());
            assertEquals(List.of("who"), binding.tools().stream().map(ToolDefinition::name).toList());
        }
        assertEquals(List.of("Root", "a.Tools", "b.Tools", "gen.Box", "nest.Outer$Hidden", "nest.Outer$Inner",
                "pkg.Hidden", "pub.PublicTools"), found.stream().sorted().toList());
    }

    // 3. Service file content

    @Test
    void theServiceFileListsSortedBindingsAndIsIdenticalOnRepeat(@TempDir final Path output) throws Exception {
        final Map<String, String> sources = new LinkedHashMap<>();
        for (final String name : List.of("z.Last", "a.First", "m.Middle")) {
            final String simple = name.substring(name.indexOf('.') + 1);
            sources.put(name.replace('.', '/') + ".java", "package " + name.substring(0, name.indexOf('.')) + ";\n"
                    + IMPORTS + "public class " + simple + " { @Tool public void run() {} }");
        }
        final Compilation first = harness.compile(Files.createDirectory(output.resolve("first")), sources);
        final Compilation second = harness.compile(Files.createDirectory(output.resolve("second")), sources);
        assertTrue(first.success(), first.diagnostics().toString());
        assertTrue(second.success(), second.diagnostics().toString());

        final String expected = Stream.of("z.Last", "a.First", "m.Middle").map(ToolBindingTest::bindingName).sorted()
                .map(name -> name + "\n").reduce("", String::concat);
        final byte[] content = Files.readAllBytes(first.classes().resolve(SERVICE_FILE));
        assertEquals(expected, new String(content, StandardCharsets.UTF_8));
        assertEquals(3, expected.lines().count());
        assertTrue(java.util.Arrays.equals(content, Files.readAllBytes(second.classes().resolve(SERVICE_FILE))));
    }

    // 4. Failure behavior

    @Test
    void anInvalidProviderBlocksTheServiceFileButNotTheValidProviderClasses(@TempDir final Path output)
            throws Exception {
        final Map<String, String> sources = new LinkedHashMap<>();
        sources.put("Good.java", IMPORTS + "public class Good { @Tool public String ok() { return \"ok\"; } }");
        sources.put("Bad.java", IMPORTS + "public class Bad { @Tool public Object bad() { return null; } }");
        final Compilation compilation = harness.compile(output, sources);
        assertFalse(compilation.success());
        assertFalse(Files.exists(compilation.classes().resolve(SERVICE_FILE)));
        final String suffix = new GeneratedNames().suffix("Good");
        assertEquals(List.of("MaegaeToolBinding_" + suffix + ".java", "MaegaeToolInvokers_" + suffix + ".java",
                "MaegaeToolMetadata_" + suffix + ".java"), compilation.generatedFiles());
    }

    /** Writes the service file in round one, so Maegae finds the resource already created. */
    private static final class ServiceFileWriter extends AbstractProcessor {
        private boolean written;

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
            if (!written) {
                written = true;
                try (final Writer writer = processingEnv.getFiler()
                        .createResource(StandardLocation.CLASS_OUTPUT, "", SERVICE_FILE).openWriter()) {
                    writer.write("other.Binding\n");
                } catch (IOException exception) {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, exception.toString());
                }
            }
            return false;
        }
    }

    @Test
    void anExistingServiceFileIsAnErrorAndIsNotMerged(@TempDir final Path output) throws Exception {
        final Compilation compilation = harness.compile(output,
                single("Tools.java", IMPORTS + "public class Tools { @Tool public void run() {} }"),
                List.of(new ServiceFileWriter(), new McpToolProcessor()));
        assertFalse(compilation.success());
        assertEquals(1, compilation.errors().size(), compilation.diagnostics().toString());
        assertTrue(compilation.errors().getFirst().getMessage(null).contains(SERVICE_FILE),
                compilation.diagnostics().toString());
        assertEquals("other.Binding\n", Files.readString(compilation.classes().resolve(SERVICE_FILE)));
    }

    // 5. All or nothing

    @Test
    void aBindingNameCollisionGeneratesNoneOfTheThreeClasses(@TempDir final Path output) throws Exception {
        final String binding = "MaegaeToolBinding_" + new GeneratedNames().suffix("Tools");
        final Map<String, String> sources = new LinkedHashMap<>();
        sources.put("Tools.java", IMPORTS + "public class Tools { @Tool public void run() {} }");
        sources.put(binding + ".java", "public class " + binding + " {}");
        final Compilation compilation = harness.compile(output, sources);
        assertFalse(compilation.success());
        assertEquals(1, compilation.errors().size(), compilation.diagnostics().toString());
        assertTrue(compilation.errors().getFirst().getMessage(null).contains(binding),
                compilation.diagnostics().toString());
        assertEquals(List.of(), compilation.generatedFiles());
        assertFalse(Files.exists(compilation.classes().resolve(SERVICE_FILE)));
    }

    // 6. Originating elements

    /** Wraps the real processor and records originating elements of source files and resources. */
    private static Processor recording(final Map<String, List<String>> origins) {
        final Processor delegate = new McpToolProcessor();
        return (Processor) Proxy.newProxyInstance(Processor.class.getClassLoader(), new Class<?>[]{Processor.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("init")) {
                        final var environment = (ProcessingEnvironment) args[0];
                        final Filer filer = (Filer) Proxy.newProxyInstance(Filer.class.getClassLoader(),
                                new Class<?>[]{Filer.class}, (ignored, filerMethod, filerArgs) -> {
                                    final String name = switch (filerMethod.getName()) {
                                        case "createSourceFile" -> filerArgs[0].toString();
                                        case "createResource" -> filerArgs[2].toString();
                                        default -> null;
                                    };
                                    if (name != null) {
                                        final Element[] elements = (Element[]) filerArgs[filerArgs.length - 1];
                                        origins.put(name, java.util.Arrays.stream(elements)
                                                .map(element -> element.getKind() + ":" + element).toList());
                                    }
                                    try {
                                        return filerMethod.invoke(environment.getFiler(), filerArgs);
                                    } catch (java.lang.reflect.InvocationTargetException exception) {
                                        throw exception.getCause();
                                    }
                                });
                        delegate.init((ProcessingEnvironment) Proxy.newProxyInstance(
                                ProcessingEnvironment.class.getClassLoader(),
                                new Class<?>[]{ProcessingEnvironment.class}, (ignored, envMethod, envArgs) -> {
                                    if (envMethod.getName().equals("getFiler")) {
                                        return filer;
                                    }
                                    try {
                                        return envMethod.invoke(environment, envArgs);
                                    } catch (java.lang.reflect.InvocationTargetException exception) {
                                        throw exception.getCause();
                                    }
                                }));
                        return null;
                    }
                    try {
                        return method.invoke(delegate, args);
                    } catch (java.lang.reflect.InvocationTargetException exception) {
                        throw exception.getCause();
                    }
                });
    }

    @Test
    void aBindingNamesItsProviderAndTheServiceFileNamesAllProviders(@TempDir final Path output) throws Exception {
        final Map<String, List<String>> origins = new LinkedHashMap<>();
        final Map<String, String> sources = new LinkedHashMap<>();
        sources.put("p/One.java", "package p;\n" + IMPORTS + "public class One { @Tool public void a() {} }");
        sources.put("p/Two.java", "package p;\n" + IMPORTS + "public class Two { @Tool public void b() {} }");
        final Compilation compilation = harness.compile(output, sources, List.of(recording(origins)));
        assertTrue(compilation.success(), compilation.diagnostics().toString());
        final List<String> binding = List.of("CLASS:p.One", "METHOD:a()");
        assertEquals(binding, origins.get(bindingName("p.One")));
        assertEquals(List.of("CLASS:p.Two", "METHOD:b()"), origins.get(bindingName("p.Two")));
        assertEquals(new TreeSet<>(List.of("CLASS:p.One", "CLASS:p.Two")),
                new TreeSet<>(origins.get(SERVICE_FILE)));
        assertEquals(2, origins.get(SERVICE_FILE).size());
    }

    // 7. No reflection in generated bindings

    @Test
    void aBindingSourceUsesNoReflection(@TempDir final Path output) throws Exception {
        final Compilation compilation = harness.compile(output, single("gen/Box.java", "package gen;\n" + IMPORTS
                + "public class Box<K extends Number, V> { @Tool public void run() {} }"));
        assertTrue(compilation.success(), compilation.diagnostics().toString());
        final String source = compilation.generatedContents().entrySet().stream()
                .filter(entry -> entry.getKey().contains("MaegaeToolBinding_")).findFirst().orElseThrow().getValue();
        assertTrue(source.contains("instanceof gen.Box<?, ?>"), source);
        assertFalse(Stream.of("java.lang.reflect", "Class.forName", "getMethod", "setAccessible")
                .anyMatch(source::contains), source);
    }

    // 8. Constants and the packaged incremental declaration

    @Test
    void theServiceNameConstantEqualsTheSpiClassName() {
        assertEquals(ToolProviderBinding.class.getName(), BindingServiceFile.SERVICE);
        assertEquals(SERVICE_FILE, BindingServiceFile.PATH);
    }

    @Test
    void theProcessorJarDeclaresTheProcessorAsAggregating() throws Exception {
        final String entryName = "META-INF/gradle/incremental.annotation.processors";
        try (final var jar = new JarFile(System.getProperty("maegae.processor.jar"))) {
            final var entry = jar.getJarEntry(entryName);
            assertNotNull(entry);
            try (final var input = jar.getInputStream(entry)) {
                assertEquals("dev.nexcraft.maegae.processor.McpToolProcessor,aggregating\n",
                        new String(input.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
    }

    // 9. A provider from another processor

    @Test
    void aProviderGeneratedInRoundOneGetsABindingAndOneServiceFileEntry(@TempDir final Path output) throws Exception {
        final Processor generator = new AbstractProcessor() {
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
                        writer.write(IMPORTS + "public class Late { @Tool public String late() { return \"l\"; } }");
                    } catch (IOException exception) {
                        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, exception.toString());
                    }
                }
                return false;
            }
        };
        final Compilation compilation = harness.compile(output,
                single("Early.java", IMPORTS + "public class Early { @Tool public String early() { return \"e\"; } }"),
                List.of(generator, new McpToolProcessor()));
        assertTrue(compilation.success(), compilation.diagnostics().toString());
        assertEquals(Stream.of("Early", "Late").map(ToolBindingTest::bindingName).sorted()
                .map(name -> name + "\n").reduce("", String::concat),
                Files.readString(compilation.classes().resolve(SERVICE_FILE)));
    }
}
