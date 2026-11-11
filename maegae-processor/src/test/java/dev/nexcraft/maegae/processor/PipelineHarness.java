package dev.nexcraft.maegae.processor;

import dev.nexcraft.maegae.annotation.Tool;
import dev.nexcraft.maegae.internal.json.JacksonJsonCodec;
import dev.nexcraft.maegae.internal.json.JsonObject;
import dev.nexcraft.maegae.internal.tool.JsonToolArguments;
import dev.nexcraft.maegae.protocol.mcp.ToolDefinition;
import dev.nexcraft.maegae.tool.ToolInvoker;
import dev.nexcraft.maegae.tool.ToolOutput;

import javax.annotation.processing.Processor;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Compiles providers with the real Java compiler and loads the generated classes.
 * Every compilation uses {@code -Xlint:all -Werror}, so a warning in generated code fails the test.
 */
final class PipelineHarness implements AutoCloseable {
    static final String IMPORTS = "import dev.nexcraft.maegae.annotation.*;\nimport java.math.*;\n";
    private static final JacksonJsonCodec CODEC = new JacksonJsonCodec();

    private final List<URLClassLoader> loaders = new ArrayList<>();

    /** Compiles with the processor registered in the processor jar, as a normal javac run does. */
    Compilation compile(final Path output, final Map<String, String> sources) throws Exception {
        return compile(output, sources, List.of());
    }

    /** Compiles with explicit processor instances instead of service discovery when the list is not empty. */
    Compilation compile(final Path output, final Map<String, String> sources, final List<Processor> processors)
            throws Exception {
        final Path classes = Files.createDirectories(output.resolve("classes"));
        final Path generated = Files.createDirectories(output.resolve("generated"));
        final List<Path> files = new ArrayList<>();
        for (final var source : sources.entrySet()) {
            final Path file = output.resolve("src").resolve(source.getKey());
            Files.createDirectories(file.getParent());
            files.add(Files.writeString(file, source.getValue(), StandardCharsets.UTF_8));
        }
        final Path core = Path.of(Tool.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        final List<String> options = new ArrayList<>(List.of("--release", "25", "-Xlint:all", "-Werror",
                "--class-path", core.toString(), "-d", classes.toString(), "-s", generated.toString()));
        if (processors.isEmpty()) {
            options.addAll(List.of("--processor-path",
                    System.getProperty("maegae.processor.jar") + File.pathSeparator + core));
        }
        final var diagnostics = new DiagnosticCollector<JavaFileObject>();
        final var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler);
        try (final var manager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            final var task = compiler.getTask(new StringWriter(), manager, diagnostics, options, null,
                    manager.getJavaFileObjectsFromPaths(files));
            if (!processors.isEmpty()) {
                task.setProcessors(processors);
            }
            task.setLocale(Locale.ROOT);
            return new Compilation(task.call(), List.copyOf(diagnostics.getDiagnostics()), classes, generated);
        }
    }

    /** Loads the generated metadata and invokers of a provider and creates a provider instance. */
    Loaded load(final Compilation compilation, final String binaryName) throws Exception {
        final var loader = new URLClassLoader(new URL[]{compilation.classes().toUri().toURL()},
                ToolInvoker.class.getClassLoader());
        loaders.add(loader);
        final String suffix = new GeneratedNames().suffix(binaryName);
        final int separator = binaryName.lastIndexOf('.');
        final String packagePrefix = separator < 0 ? "" : binaryName.substring(0, separator + 1);
        final Class<?> providerClass = loader.loadClass(binaryName);
        final var constructor = providerClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        final Object provider = constructor.newInstance();
        final Class<?> metadata = loader.loadClass(packagePrefix + "MaegaeToolMetadata_" + suffix);
        final Class<?> adapter = loader.loadClass(packagePrefix + "MaegaeToolInvokers_" + suffix);
        @SuppressWarnings("unchecked") final List<ToolDefinition> tools =
                (List<ToolDefinition>) metadata.getMethod("tools").invoke(null);
        @SuppressWarnings("unchecked") final Map<String, ToolInvoker> invokers =
                (Map<String, ToolInvoker>) adapter.getMethod("invokers", providerClass).invoke(null, provider);
        return new Loaded(provider, providerClass, adapter, tools, invokers);
    }

    static ToolOutput call(final Map<String, ToolInvoker> invokers, final String tool, final String json)
            throws Exception {
        final ToolInvoker invoker = invokers.get(tool);
        assertNotNull(invoker, "Missing Tool " + tool + " in " + invokers.keySet());
        return invoker.invoke(JsonToolArguments.of((JsonObject) CODEC.parse(json)));
    }

    static JsonObject parse(final String json) {
        return (JsonObject) CODEC.parse(json);
    }

    @Override
    public void close() throws Exception {
        for (final URLClassLoader loader : loaders) {
            loader.close();
        }
    }

    record Compilation(boolean success, List<Diagnostic<? extends JavaFileObject>> diagnostics, Path classes,
                       Path generated) {
        List<Diagnostic<? extends JavaFileObject>> errors() {
            return diagnostics.stream().filter(d -> d.getKind() == Diagnostic.Kind.ERROR).toList();
        }

        /** Generated source files as sorted paths relative to the output root, with {@code /} separators. */
        List<String> generatedFiles() throws Exception {
            try (final Stream<Path> files = Files.walk(generated)) {
                return files.filter(Files::isRegularFile).map(file -> generated.relativize(file).toString()
                        .replace(File.separatorChar, '/')).sorted().toList();
            }
        }

        /** Content of every generated source file by relative path. */
        Map<String, String> generatedContents() throws Exception {
            final Map<String, String> contents = new LinkedHashMap<>();
            for (final String file : generatedFiles()) {
                contents.put(file, Files.readString(generated.resolve(file)));
            }
            return contents;
        }

        long classFileCount() throws Exception {
            try (final Stream<Path> files = Files.walk(classes)) {
                return files.filter(file -> file.toString().endsWith(".class")).count();
            }
        }
    }

    record Loaded(Object provider, Class<?> providerClass, Class<?> adapter, List<ToolDefinition> tools,
                  Map<String, ToolInvoker> invokers) { }
}
