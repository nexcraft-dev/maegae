package dev.nexcraft.maegae.annotation;

import com.sun.source.util.JavacTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Protects the public metadata contract without requiring runtime retention of annotation usages. */
class ToolAnnotationContractTest {
    @Test
    void toolDeclaresTheExactMethodMetadataContract() {
        assertDeclaration(Tool.class, ElementType.METHOD, Map.of("name", "", "description", ""));
    }

    @Test
    void argDeclaresTheExactParameterMetadataContract() {
        assertDeclaration(Arg.class, ElementType.PARAMETER,
                Map.of("name", "", "description", "", "required", true));
    }

    @Test
    void compilerPreservesMetadataAndSourceParameterNamesWithoutAProcessorOrParameterReflection(
            @TempDir final Path output) throws Exception {
        final Path source = output.resolve("ToolUsage.java");
        Files.writeString(source, """
                package example;
                import dev.nexcraft.maegae.annotation.Tool;
                import dev.nexcraft.maegae.annotation.Arg;
                public class ToolUsage {
                    @Tool
                    public int sum(@Arg int first, int second) { return first + second; }

                    @Tool(name = "renamed", description = " tool documentation ")
                    public String label(@Arg(name = "label", description = " value documentation ",
                                             required = false) int value) {
                        return Integer.toString(value);
                    }
                }
                """);
        final var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "Contract tests require the configured JDK 25 compiler");
        final var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (final var files = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            final var task = (JavacTask) compiler.getTask(null, files, diagnostics,
                    List.of("--release", "25", "-proc:none", "-classpath", System.getProperty("java.class.path"),
                            "-d", output.toString()), null, files.getJavaFileObjects(source));
            task.analyze();
            assertFalse(diagnostics.getDiagnostics().stream().anyMatch(d -> d.getKind() == Diagnostic.Kind.ERROR),
                    diagnostics.getDiagnostics().toString());
            assertUsageMetadata(task);
            task.generate();
            assertTrue(Files.isRegularFile(output.resolve("example/ToolUsage.class")));
        }
    }

    /** Checks standard declaration metadata and element defaults, not runtime annotation usages. */
    private static void assertDeclaration(final Class<? extends Annotation> type, final ElementType target,
                                          final Map<String, Object> defaults) {
        assertTrue(Modifier.isPublic(type.getModifiers()));
        assertEquals(List.of(target), List.of(type.getAnnotation(Target.class).value()));
        assertEquals(RetentionPolicy.CLASS, type.getAnnotation(Retention.class).value());
        final var elements = Arrays.stream(type.getDeclaredMethods()).collect(Collectors.toMap(
                method -> method.getName(), method -> method.getDefaultValue()));
        assertEquals(defaults, elements);
        for (final var method : type.getDeclaredMethods()) {
            assertEquals(defaults.get(method.getName()) instanceof Boolean ? boolean.class : String.class,
                    method.getReturnType());
        }
    }

    /** Checks compiler-model values without resolving fallback names or invoking fixture methods. */
    private static void assertUsageMetadata(final JavacTask task) {
        final var fixture = task.getElements().getTypeElement("example.ToolUsage");
        final var sum = method(fixture, "sum");
        assertEquals(Map.of("name", "", "description", ""), annotationValues(task, sum, Tool.class));
        assertEquals(List.of("first", "second"), sum.getParameters().stream()
                .map(parameter -> parameter.getSimpleName().toString()).toList());
        assertEquals(Map.of("name", "", "description", "", "required", true),
                annotationValues(task, sum.getParameters().getFirst(), Arg.class));
        assertTrue(sum.getParameters().get(1).getAnnotationMirrors().isEmpty());

        final var label = method(fixture, "label");
        assertEquals(Map.of("name", "renamed", "description", " tool documentation "),
                annotationValues(task, label, Tool.class));
        assertEquals(Map.of("name", "label", "description", " value documentation ", "required", false),
                annotationValues(task, label.getParameters().getFirst(), Arg.class));
    }

    /** Finds a named source method in the compiler model. */
    private static ExecutableElement method(final TypeElement type, final String name) {
        return ElementFilter.methodsIn(type.getEnclosedElements()).stream()
                .filter(method -> method.getSimpleName().contentEquals(name)).findFirst().orElseThrow();
    }

    /** Reads declared and defaulted annotation values directly from the compiler model. */
    private static Map<String, Object> annotationValues(final JavacTask task, final Element element,
                                                       final Class<? extends Annotation> type) {
        final var mirror = element.getAnnotationMirrors().stream()
                .filter(annotation -> annotation.getAnnotationType().toString().equals(type.getCanonicalName()))
                .findFirst().orElseThrow();
        return task.getElements().getElementValuesWithDefaults(mirror).entrySet().stream().collect(Collectors.toMap(
                entry -> entry.getKey().getSimpleName().toString(), entry -> entry.getValue().getValue()));
    }
}
