package dev.nexcraft.maegae.transport.netty.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import dev.nexcraft.maegae.transport.netty.NettyFixtures;
import dev.nexcraft.maegae.transport.netty.http.HttpFixtures;
import dev.nexcraft.maegae.transport.netty.internal.InternalFixtures;
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

import static dev.nexcraft.maegae.transport.netty.architecture.NettyArchitectureRules.*;
import static org.junit.jupiter.api.Assertions.*;

class NettyArchitectureRulesTest {
    private static final List<ArchRule> RULES = List.of(MODULE_INDEPENDENCE, INTERNAL_DIRECTION,
            ENTRY_POINT_DIRECTION, INTERNAL_ENCAPSULATION, NO_PACKAGE_CYCLES);

    @Test
    void allRulesAcceptEmptyProductionOutput(@TempDir Path directory) {
        var empty = NettyArchitectureTest.importProductionClasses(List.of(directory, directory.resolve("missing")));
        RULES.forEach(rule -> rule.check(empty));
    }

    @Test
    void missingPackagesAndImplementationOnlyInternalUsageAreAllowed() {
        var classes = new ClassFileImporter().importClasses(NettyFixtures.ImplementationOnly.class,
                NettyFixtures.RecursiveBound.class);
        RULES.forEach(rule -> rule.check(classes));
    }

    @Test
    void approvedDirectionsCoreContractsJdkAndNettyApisAreAllowed() {
        var classes = new ClassFileImporter().importClasses(NettyFixtures.Valid.class,
                HttpFixtures.Valid.class, InternalFixtures.Valid.class);
        RULES.forEach(rule -> rule.check(classes));
        // A root contract dependency is allowed, but combining opposite package edges would form a cycle.
        var rootContractUsage = new ClassFileImporter().importClasses(HttpFixtures.OtherRootDependency.class,
                NettyFixtures.RootContract.class);
        RULES.forEach(rule -> rule.check(rootContractUsage));
    }

    @ParameterizedTest(name = "{0} must not depend on {1}")
    @MethodSource("forbiddenModuleDependencies")
    void detectsForbiddenModuleDependencies(Class<?> fixture, String target) {
        assertViolation(MODULE_INDEPENDENCE, fixture, target);
    }

    static Stream<Arguments> forbiddenModuleDependencies() {
        return Stream.of(
                Arguments.of(NettyFixtures.ProcessorDependency.class, "processor.ProcessorFixture"),
                Arguments.of(NettyFixtures.ExampleDependency.class, "example.ExampleFixture"),
                Arguments.of(NettyFixtures.CoreInternalDependency.class, "internal.CoreInternalFixture"));
    }

    @ParameterizedTest(name = "{1} violates {0}")
    @MethodSource("forbiddenDirections")
    void detectsForbiddenDirections(ArchRule rule, Class<?> fixture, String target) {
        assertViolation(rule, fixture, target);
    }

    static Stream<Arguments> forbiddenDirections() {
        return Stream.of(
                Arguments.of(INTERNAL_DIRECTION, InternalFixtures.HttpDependency.class, "http.HttpFixtures$Valid"),
                Arguments.of(ENTRY_POINT_DIRECTION, HttpFixtures.EntryDependency.class, "netty.NettyTransport"),
                Arguments.of(ENTRY_POINT_DIRECTION, HttpFixtures.NestedEntryDependency.class, "NettyTransport$Nested"));
    }

    @ParameterizedTest(name = "API leak: {0}")
    @MethodSource("internalLeaks")
    void detectsInternalTypesInApiSignatures(Class<?> fixture, String internalType) {
        assertViolation(INTERNAL_ENCAPSULATION, fixture, "internal.InternalFixtures$" + internalType);
    }

    static Stream<Arguments> internalLeaks() {
        return Stream.of(
                Arguments.of(NettyFixtures.FieldLeak.class, "Value"),
                Arguments.of(NettyFixtures.ConstructorLeak.class, "Value"),
                Arguments.of(NettyFixtures.ReturnLeak.class, "Value"),
                Arguments.of(NettyFixtures.ParameterLeak.class, "Value"),
                Arguments.of(NettyFixtures.ExceptionLeak.class, "Failure"),
                Arguments.of(NettyFixtures.ConstructorExceptionLeak.class, "Failure"),
                Arguments.of(NettyFixtures.ArrayLeak.class, "Value"),
                Arguments.of(NettyFixtures.GenericLeak.class, "Value"),
                Arguments.of(NettyFixtures.LowerBoundLeak.class, "Value"),
                Arguments.of(NettyFixtures.ClassBoundLeak.class, "Value"),
                Arguments.of(NettyFixtures.MethodBoundLeak.class, "Value"),
                Arguments.of(NettyFixtures.SuperclassLeak.class, "Value"),
                Arguments.of(NettyFixtures.InterfaceLeak.class, "Contract"),
                Arguments.of(NettyFixtures.GenericSuperclassLeak.class, "Value"),
                Arguments.of(NettyFixtures.GenericInterfaceLeak.class, "Value"),
                Arguments.of(NettyFixtures.InheritedLeak.class, "Value"),
                Arguments.of(NettyFixtures.ProtectedOwner.fixtureType(), "Value"));
    }

    @Test
    void inaccessibleTypesDoNotBecomePublicApi() {
        INTERNAL_ENCAPSULATION.check(new ClassFileImporter().importClasses(
                NettyFixtures.hiddenApiTypes().toArray(Class<?>[]::new)));
    }

    @Test
    void detectsCyclesBetweenFirstLevelPackages() {
        var classes = new ClassFileImporter().importClasses(HttpFixtures.Cycle.class, InternalFixtures.Cycle.class);
        AssertionError failure = assertThrows(AssertionError.class, () -> NO_PACKAGE_CYCLES.check(classes));
        assertTrue(failure.getMessage().contains("http"), failure.getMessage());
        assertTrue(failure.getMessage().contains("internal"), failure.getMessage());
    }

    @Test
    void detectsCyclesIncludingEntryPackage() {
        var classes = new ClassFileImporter().importClasses(NettyFixtures.RootCycle.class, InternalFixtures.EntryCycle.class);
        AssertionError failure = assertThrows(AssertionError.class, () -> NO_PACKAGE_CYCLES.check(classes));
        assertTrue(failure.getMessage().contains("<entry>"), failure.getMessage());
        assertTrue(failure.getMessage().contains("internal"), failure.getMessage());
    }

    @Test
    void productionScanFindsFutureClassesWithoutImportingTestFixtures(@TempDir Path output) throws Exception {
        Path source = output.resolve("FutureNettyClass.java");
        Files.writeString(source, """
                package dev.nexcraft.maegae.transport.netty.http.detail;
                public class FutureNettyClass {
                    private dev.nexcraft.maegae.transport.netty.NettyTransport entry;
                }
                """);
        int result = ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "--release", "25", "-classpath", System.getProperty("java.class.path"),
                "-d", output.toString(), source.toString());
        assertEquals(0, result, "Architecture scanner fixture must compile");
        var classes = NettyArchitectureTest.importProductionClasses(List.of(output));
        assertEquals(List.of("dev.nexcraft.maegae.transport.netty.http.detail.FutureNettyClass"),
                classes.stream().map(type -> type.getName()).toList(),
                "Only supplied main output is imported, even with fixtures on the classpath");
        AssertionError failure = assertThrows(AssertionError.class, () -> ENTRY_POINT_DIRECTION.check(classes));
        assertTrue(failure.getMessage().contains("FutureNettyClass"), failure.getMessage());
        assertTrue(failure.getMessage().contains("NettyTransport"), failure.getMessage());
    }

    private static void assertViolation(ArchRule rule, Class<?> fixture, String target) {
        var classes = new ClassFileImporter().importClasses(fixture);
        AssertionError failure = assertThrows(AssertionError.class, () -> rule.check(classes));
        assertTrue(failure.getMessage().contains(rule.getDescription()), failure.getMessage());
        assertTrue(failure.getMessage().contains(fixture.getName()), failure.getMessage());
        assertTrue(failure.getMessage().contains(target), failure.getMessage());
    }
}
