package dev.nexcraft.maegae.processor.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import dev.nexcraft.maegae.processor.EntryFixtures;
import dev.nexcraft.maegae.processor.generator.GeneratorFixtures;
import dev.nexcraft.maegae.processor.model.ModelFixtures;
import dev.nexcraft.maegae.processor.schema.SchemaFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static dev.nexcraft.maegae.processor.architecture.ProcessorArchitectureRules.*;
import static org.junit.jupiter.api.Assertions.*;

class ProcessorArchitectureRulesTest {
    private static final List<ArchRule> RULES = List.of(MODULE_INDEPENDENCE, MODEL_DIRECTION,
            SCHEMA_DIRECTION, ENTRY_POINT_DIRECTION, NO_PACKAGE_CYCLES);

    @Test
    void allRulesAcceptEmptyProductionOutput(@TempDir Path directory) {
        JavaClasses empty = ProcessorArchitectureTest.importProductionClasses(
                List.of(directory, directory.resolve("missing")));
        RULES.forEach(rule -> rule.check(empty));
    }

    @Test
    void missingPackagesAndSharedCoreContractsAreAllowed() {
        var classes = new ClassFileImporter().importClasses(ModelFixtures.Metadata.class);
        RULES.forEach(rule -> rule.check(classes));
    }

    @ParameterizedTest(name = "{0} must not depend on {1}")
    @MethodSource("forbiddenModuleDependencies")
    void detectsForbiddenModuleDependencies(Class<?> fixture, String target) {
        assertViolation(MODULE_INDEPENDENCE, fixture, target);
    }

    static Stream<Arguments> forbiddenModuleDependencies() {
        return Stream.of(
                Arguments.of(EntryFixtures.NettyDependency.class, "io.netty.fixture.ProcessorNetworkFixture"),
                Arguments.of(EntryFixtures.TransportDependency.class, "transport.netty.ProcessorTransportFixture"),
                Arguments.of(EntryFixtures.ExampleDependency.class, "example.ProcessorExampleFixture"),
                Arguments.of(EntryFixtures.InternalDependency.class, "internal.ProcessorInternalFixture"));
    }

    @ParameterizedTest(name = "{1} violates {0}")
    @MethodSource("forbiddenPackageDirections")
    void detectsForbiddenPackageDirections(ArchRule rule, Class<?> fixture, String target) {
        assertViolation(rule, fixture, target);
    }

    static Stream<Arguments> forbiddenPackageDirections() {
        return Stream.of(
                Arguments.of(MODEL_DIRECTION, ModelFixtures.SchemaDependency.class, "schema.SchemaFixtures$Valid"),
                Arguments.of(MODEL_DIRECTION, ModelFixtures.GeneratorDependency.class, "generator.GeneratorFixtures$Valid"),
                Arguments.of(SCHEMA_DIRECTION, SchemaFixtures.GeneratorDependency.class, "generator.GeneratorFixtures$Valid"),
                Arguments.of(ENTRY_POINT_DIRECTION, ModelFixtures.EntryDependency.class, "processor.EntryFixtures$Contract"),
                Arguments.of(ENTRY_POINT_DIRECTION, SchemaFixtures.EntryDependency.class, "processor.EntryFixtures$Contract"),
                Arguments.of(ENTRY_POINT_DIRECTION, GeneratorFixtures.EntryDependency.class, "processor.EntryFixtures$Contract"));
    }

    @Test
    void approvedDirectionsAndStandardProcessingApisAreAllowed() {
        var classes = new ClassFileImporter().importClasses(EntryFixtures.Valid.class, ModelFixtures.Metadata.class,
                SchemaFixtures.Valid.class, GeneratorFixtures.Valid.class);
        RULES.forEach(rule -> rule.check(classes));
    }

    @Test
    void detectsCyclesBetweenFirstLevelPackages() {
        var classes = new ClassFileImporter().importClasses(ModelFixtures.Cycle.class, SchemaFixtures.Cycle.class);
        AssertionError failure = assertThrows(AssertionError.class, () -> NO_PACKAGE_CYCLES.check(classes));
        assertTrue(failure.getMessage().contains("model"), failure.getMessage());
        assertTrue(failure.getMessage().contains("schema"), failure.getMessage());
    }

    @Test
    void detectsCyclesIncludingEntryPoint() {
        var classes = new ClassFileImporter().importClasses(EntryFixtures.Cycle.class, GeneratorFixtures.EntryCycle.class);
        AssertionError failure = assertThrows(AssertionError.class, () -> NO_PACKAGE_CYCLES.check(classes));
        assertTrue(failure.getMessage().contains("<entry>"), failure.getMessage());
        assertTrue(failure.getMessage().contains("generator"), failure.getMessage());
    }

    @Test
    void productionScanFindsFutureClassesWithoutImportingTestFixtures(@TempDir Path output) throws Exception {
        Path source = output.resolve("FutureProcessorClass.java");
        Files.writeString(source, """
                package dev.nexcraft.maegae.processor.model.detail;
                public class FutureProcessorClass {
                    private dev.nexcraft.maegae.processor.EntryFixtures.Contract entry;
                }
                """);
        int result = ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "--release", "25", "-classpath", System.getProperty("java.class.path"),
                "-d", output.toString(), source.toString());
        assertEquals(0, result, "Architecture scanner fixture must compile");
        var classes = ProcessorArchitectureTest.importProductionClasses(List.of(output));
        assertEquals(List.of("dev.nexcraft.maegae.processor.model.detail.FutureProcessorClass"),
                classes.stream().map(type -> type.getName()).toList(),
                "Only supplied production output is imported, even with test fixtures on the classpath");
        AssertionError failure = assertThrows(AssertionError.class, () -> ENTRY_POINT_DIRECTION.check(classes));
        assertTrue(failure.getMessage().contains("FutureProcessorClass"), failure.getMessage());
        assertTrue(failure.getMessage().contains("EntryFixtures$Contract"), failure.getMessage());
    }

    private static void assertViolation(ArchRule rule, Class<?> fixture, String target) {
        var classes = new ClassFileImporter().importClasses(fixture);
        AssertionError failure = assertThrows(AssertionError.class, () -> rule.check(classes));
        assertTrue(failure.getMessage().contains(rule.getDescription()), failure.getMessage());
        assertTrue(failure.getMessage().contains(fixture.getName()), failure.getMessage());
        assertTrue(failure.getMessage().contains(target), failure.getMessage());
    }
}
