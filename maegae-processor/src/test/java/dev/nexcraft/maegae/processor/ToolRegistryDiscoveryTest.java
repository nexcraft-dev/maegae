package dev.nexcraft.maegae.processor;

import dev.nexcraft.maegae.internal.tool.JsonToolArguments;
import dev.nexcraft.maegae.processor.PipelineHarness.Compilation;
import dev.nexcraft.maegae.protocol.mcp.ToolDefinition;
import dev.nexcraft.maegae.runtime.ToolRegistry;
import dev.nexcraft.maegae.tool.ToolInvoker;
import dev.nexcraft.maegae.tool.ToolOutput;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import static dev.nexcraft.maegae.processor.PipelineHarness.IMPORTS;
import static org.junit.jupiter.api.Assertions.*;

/** Tests the Tool registry with providers compiled by the real processor and found by the real ServiceLoader. */
class ToolRegistryDiscoveryTest {
    private final PipelineHarness harness = new PipelineHarness();
    private final List<URLClassLoader> loaders = new ArrayList<>();

    @AfterEach
    void close() throws Exception {
        harness.close();
        for (final URLClassLoader loader : loaders) {
            loader.close();
        }
    }

    private static Map<String, String> providers() {
        final Map<String, String> sources = new LinkedHashMap<>();
        sources.put("Top.java", IMPORTS + "public class Top {\n"
                + "  @Tool(description = \"Adds\") public int add(int a, int b) { return a + b; }\n"
                + "  @Tool public String zulu() { return \"top-zulu\"; }\n}");
        sources.put("Outer.java", IMPORTS + "public class Outer {\n public static class Nested {\n"
                + "  @Tool public String mid() { return \"nested-mid\"; }\n } }");
        sources.put("other/Elsewhere.java", "package other;\n" + IMPORTS + "public class Elsewhere {\n"
                + "  @Tool public String alpha() { return \"other-alpha\"; }\n}");
        return sources;
    }

    private URLClassLoader loader(final Compilation compilation) throws Exception {
        final var loader = new URLClassLoader(new URL[]{compilation.classes().toUri().toURL()},
                ToolInvoker.class.getClassLoader());
        loaders.add(loader);
        return loader;
    }

    private static Object instance(final ClassLoader loader, final String name) throws Exception {
        return loader.loadClass(name).getConstructor().newInstance();
    }

    /** Runs the action with the loader as thread context class loader and restores the old one. */
    private static <T> T withContext(final ClassLoader loader, final Callable<T> action) throws Exception {
        final Thread thread = Thread.currentThread();
        final ClassLoader previous = thread.getContextClassLoader();
        thread.setContextClassLoader(loader);
        try {
            return action.call();
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    private static ToolOutput run(final ToolRegistry registry, final String name, final String json) throws Exception {
        return registry.find(name).orElseThrow().invoke(JsonToolArguments.of(PipelineHarness.parse(json)));
    }

    @Test
    void discoversGeneratedBindingsAndRunsTools(@TempDir final Path output) throws Exception {
        final Compilation compilation = harness.compile(output, providers());
        assertTrue(compilation.success(), compilation.diagnostics().toString());
        final var loader = loader(compilation);

        final List<ToolDefinition> expected = new ArrayList<>();
        for (final String name : List.of("Top", "Outer$Nested", "other.Elsewhere")) {
            expected.addAll(harness.load(compilation, name).tools());
        }
        expected.sort(Comparator.comparing(ToolDefinition::name));
        assertEquals(List.of("add", "alpha", "mid", "zulu"), expected.stream().map(ToolDefinition::name).toList());

        final Object top = instance(loader, "Top");
        final Object nested = instance(loader, "Outer$Nested");
        final Object elsewhere = instance(loader, "other.Elsewhere");
        final ToolRegistry forward = withContext(loader, () -> ToolRegistry.of(List.of(top, nested, elsewhere)));
        final ToolRegistry reverse = withContext(loader, () -> ToolRegistry.of(List.of(elsewhere, nested, top)));

        assertEquals(expected, forward.tools());
        assertEquals(forward.tools(), reverse.tools());
        assertEquals(ToolOutput.text("5"), run(forward, "add", "{\"a\":2,\"b\":3}"));
        assertEquals(ToolOutput.text("top-zulu"), run(reverse, "zulu", "{}"));
        assertEquals(ToolOutput.text("nested-mid"), run(forward, "mid", "{}"));
        assertEquals(ToolOutput.text("other-alpha"), run(forward, "alpha", "{}"));
        assertTrue(forward.find("unknown").isEmpty());
    }

    @Test
    void contextClassLoaderIsRestoredAfterTheTest(@TempDir final Path output) throws Exception {
        final Thread thread = Thread.currentThread();
        final ClassLoader before = thread.getContextClassLoader();
        final Compilation compilation = harness.compile(output, providers());
        withContext(loader(compilation), () -> ToolRegistry.of(List.of()));
        assertSame(before, thread.getContextClassLoader());
    }

    @Test
    void duplicateToolNameAcrossRealProvidersIsRejected(@TempDir final Path output) throws Exception {
        final Map<String, String> sources = new LinkedHashMap<>();
        sources.put("One.java", IMPORTS + "public class One { @Tool public String same() { return \"1\"; } }");
        sources.put("Two.java", IMPORTS + "public class Two { @Tool public String same() { return \"2\"; } }");
        final Compilation compilation = harness.compile(output, sources);
        assertTrue(compilation.success(), compilation.diagnostics().toString());
        final var loader = loader(compilation);
        final Object one = instance(loader, "One");
        final Object two = instance(loader, "Two");

        final String expected = "Duplicate Tool name 'same' in providers One, Two.";
        assertEquals(expected, assertThrows(IllegalArgumentException.class,
                () -> withContext(loader, () -> ToolRegistry.of(List.of(two, one)))).getMessage());
        assertEquals("Duplicate Tool name 'same' in providers One, One.", assertThrows(IllegalArgumentException.class,
                () -> withContext(loader, () -> ToolRegistry.of(List.of(one, instance(loader, "One"))))).getMessage());
        assertEquals("Duplicate Tool name 'same' in providers One, One.", assertThrows(IllegalArgumentException.class,
                () -> withContext(loader, () -> ToolRegistry.of(List.of(one, one)))).getMessage());
    }

    @Test
    void providersWithoutAnExactBindingAreRejected(@TempDir final Path output) throws Exception {
        final Map<String, String> sources = new LinkedHashMap<>(providers());
        sources.put("Plain.java", "public class Plain { }");
        sources.put("Sub.java", "public class Sub extends Top { }");
        sources.put("Factory.java", "public class Factory { public static Object anonymous() { return new Top() { }; } }");
        final Compilation compilation = harness.compile(output, sources);
        assertTrue(compilation.success(), compilation.diagnostics().toString());
        final var loader = loader(compilation);
        final Object anonymous = loader.loadClass("Factory").getMethod("anonymous").invoke(null);

        for (final Object unbound : List.of(instance(loader, "Plain"), instance(loader, "Sub"), anonymous)) {
            final String message = assertThrows(IllegalStateException.class,
                    () -> withContext(loader, () -> ToolRegistry.of(List.of(unbound)))).getMessage();
            assertEquals("No Tool binding found for provider class " + unbound.getClass().getName()
                    + ". Add maegae-processor to annotationProcessor and rebuild. "
                    + "Subclasses and anonymous classes of a provider are not supported.", message);
        }
    }

    @Test
    void registryBuiltWithoutServiceFileFailsForAnyProvider(@TempDir final Path output) throws Exception {
        final Compilation compilation = harness.compile(output, providers());
        final Object top = instance(loader(compilation), "Top");
        // The thread context class loader of this test does not see the compiled output.
        assertThrows(IllegalStateException.class, () -> ToolRegistry.of(List.of(top)));
    }
}
