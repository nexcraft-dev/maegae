package dev.nexcraft.maegae.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import dev.nexcraft.maegae.CoreFixtures;
import dev.nexcraft.maegae.annotation.AnnotationFixtures;
import dev.nexcraft.maegae.protocol.ProtocolFixtures;
import dev.nexcraft.maegae.tool.ToolFixtures;
import dev.nexcraft.maegae.transport.TransportFixtures;
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

import static dev.nexcraft.maegae.architecture.CoreArchitectureRules.*;
import static org.junit.jupiter.api.Assertions.*;

class CoreArchitectureRulesTest {
    private static final List<ArchRule> RULES = List.of(CORE_INDEPENDENCE, INTERNAL_ENCAPSULATION,
            TRANSPORT_INDEPENDENCE, ANNOTATION_INDEPENDENCE, NO_PACKAGE_CYCLES);

    @Test
    void allRulesAcceptEmptyProductionOutput(@TempDir Path directory) {
        JavaClasses empty = CoreArchitectureTest.importProductionClasses(
                List.of(directory, directory.resolve("missing")));
        RULES.forEach(rule -> rule.check(empty));
    }

    @Test
    void missingPackagesAndImplementationOnlyInternalUsageAreAllowed() {
        JavaClasses classes = new ClassFileImporter().importClasses(CoreFixtures.ImplementationOnly.class,
                CoreFixtures.RecursiveBound.class, CoreFixtures.ProtectedNestedOwner.class);
        RULES.forEach(rule -> rule.check(classes));
    }

    @ParameterizedTest(name = "{0} must not depend on {1}")
    @MethodSource("forbiddenModuleDependencies")
    void detectsForbiddenModuleDependencies(Class<?> fixture, String target) {
        assertViolation(CORE_INDEPENDENCE, fixture, target);
    }

    static Stream<Arguments> forbiddenModuleDependencies() {
        return Stream.of(
                Arguments.of(CoreFixtures.NettyDependency.class, "io.netty.fixture.NetworkFixture"),
                Arguments.of(CoreFixtures.ProcessorDependency.class, "processor.ProcessorFixture"),
                Arguments.of(CoreFixtures.NettyModuleDependency.class, "transport.netty.NettyModuleFixture"),
                Arguments.of(CoreFixtures.ExampleDependency.class, "example.ExampleFixture"));
    }

    @ParameterizedTest(name = "API leak: {0}")
    @MethodSource("internalLeaks")
    void detectsInternalTypesInApiSignatures(Class<?> fixture, String internalType) {
        assertViolation(INTERNAL_ENCAPSULATION, fixture, "internal.json.InternalFixtures$" + internalType);
    }

    static Stream<Arguments> internalLeaks() {
        return Stream.of(
                Arguments.of(CoreFixtures.FieldLeak.class, "Value"),
                Arguments.of(CoreFixtures.ConstructorLeak.class, "Value"),
                Arguments.of(CoreFixtures.ReturnLeak.class, "Value"),
                Arguments.of(CoreFixtures.ParameterLeak.class, "Value"),
                Arguments.of(CoreFixtures.ExceptionLeak.class, "Failure"),
                Arguments.of(CoreFixtures.ConstructorExceptionLeak.class, "Failure"),
                Arguments.of(CoreFixtures.ArrayLeak.class, "Value"),
                Arguments.of(CoreFixtures.GenericLeak.class, "Value"),
                Arguments.of(CoreFixtures.LowerBoundLeak.class, "Value"),
                Arguments.of(CoreFixtures.ClassBoundLeak.class, "Value"),
                Arguments.of(CoreFixtures.MethodBoundLeak.class, "Value"),
                Arguments.of(CoreFixtures.SuperclassLeak.class, "Value"),
                Arguments.of(CoreFixtures.InterfaceLeak.class, "Contract"),
                Arguments.of(CoreFixtures.GenericInterfaceLeak.class, "Value"),
                Arguments.of(CoreFixtures.GenericSuperclassLeak.class, "Value"),
                Arguments.of(CoreFixtures.InheritedLeak.class, "Value"),
                Arguments.of(CoreFixtures.ProtectedNestedOwner.fixtureType(), "Value"));
    }

    @Test
    void inaccessibleTypesDoNotBecomePublicApi() throws ClassNotFoundException {
        INTERNAL_ENCAPSULATION.check(new ClassFileImporter().importClasses(
                Class.forName("dev.nexcraft.maegae.CoreFixtures$HiddenApi"),
                Class.forName("dev.nexcraft.maegae.CoreFixtures$HiddenApi$PublicNested")));
    }

    @ParameterizedTest(name = "{1} is forbidden by {0}")
    @MethodSource("contractViolations")
    void detectsForbiddenContractDependencies(ArchRule rule, Class<?> fixture, String target) {
        assertViolation(rule, fixture, target);
    }

    static Stream<Arguments> contractViolations() {
        return Stream.of(
                Arguments.of(TRANSPORT_INDEPENDENCE, TransportFixtures.ExternalNetwork.class, "external.network.NetworkContract"),
                Arguments.of(TRANSPORT_INDEPENDENCE, TransportFixtures.Netty.class, "io.netty.fixture.NetworkFixture"),
                Arguments.of(TRANSPORT_INDEPENDENCE, TransportFixtures.Internal.class, "internal.json.InternalFixtures$Value"),
                Arguments.of(TRANSPORT_INDEPENDENCE, TransportFixtures.HiddenContractDependency.class, "TransportFixtures$HiddenContract"),
                Arguments.of(ANNOTATION_INDEPENDENCE, AnnotationFixtures.ToolDependency.class, "tool.ToolFixtures$Contract"),
                Arguments.of(ANNOTATION_INDEPENDENCE, AnnotationFixtures.ProcessorDependency.class, "processor.ProcessorFixture"),
                Arguments.of(ANNOTATION_INDEPENDENCE, AnnotationFixtures.NettyDependency.class, "io.netty.fixture.NetworkFixture"));
    }

    @Test
    void jdkAndPublicCoreContractsAreAllowed() {
        TRANSPORT_INDEPENDENCE.check(new ClassFileImporter().importClasses(TransportFixtures.Valid.class));
        ANNOTATION_INDEPENDENCE.check(new ClassFileImporter().importClasses(AnnotationFixtures.Valid.class));
    }

    @Test
    void detectsCyclesBetweenFirstLevelPackages() {
        var classes = new ClassFileImporter().importClasses(ProtocolFixtures.Cycle.class, ToolFixtures.Cycle.class);
        AssertionError failure = assertThrows(AssertionError.class, () -> NO_PACKAGE_CYCLES.check(classes));
        assertTrue(failure.getMessage().contains("protocol"), failure.getMessage());
        assertTrue(failure.getMessage().contains("tool"), failure.getMessage());
    }

    @Test
    void detectsCyclesIncludingBootstrap() {
        var classes = new ClassFileImporter().importClasses(CoreFixtures.BootstrapCycle.class, ToolFixtures.BootstrapCycle.class);
        AssertionError failure = assertThrows(AssertionError.class, () -> NO_PACKAGE_CYCLES.check(classes));
        assertTrue(failure.getMessage().contains("bootstrap"), failure.getMessage());
        assertTrue(failure.getMessage().contains("tool"), failure.getMessage());
    }

    @Test
    void allowsOneWayPackageDependencies() {
        NO_PACKAGE_CYCLES.check(new ClassFileImporter().importClasses(ProtocolFixtures.OneWay.class, ToolFixtures.Contract.class));
    }

    @Test
    void productionScanFindsFutureClassesWithoutImportingTestFixtures(@TempDir Path output) throws Exception {
        Path source = output.resolve("FutureCore.java");
        Files.writeString(source, """
                package dev.nexcraft.maegae;
                public class FutureCore {
                    private io.netty.fixture.NetworkFixture forbidden;
                }
                """);
        int result = ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "--release", "25", "-classpath", System.getProperty("java.class.path"),
                "-d", output.toString(), source.toString());
        assertEquals(0, result, "Future production fixture must compile");
        JavaClasses classes = CoreArchitectureTest.importProductionClasses(List.of(output));
        assertEquals(List.of("dev.nexcraft.maegae.FutureCore"), classes.stream().map(type -> type.getName()).toList(),
                "Only the supplied main output is scanned, even when test fixtures are on the classpath");
        AssertionError failure = assertThrows(AssertionError.class, () -> CORE_INDEPENDENCE.check(classes));
        assertTrue(failure.getMessage().contains("io.netty.fixture.NetworkFixture"), failure.getMessage());
    }

    private static void assertViolation(ArchRule rule, Class<?> fixture, String target) {
        var classes = new ClassFileImporter().importClasses(fixture);
        AssertionError failure = assertThrows(AssertionError.class, () -> rule.check(classes));
        assertTrue(failure.getMessage().contains(rule.getDescription()), failure.getMessage());
        assertTrue(failure.getMessage().contains(fixture.getName()), failure.getMessage());
        assertTrue(failure.getMessage().contains(target), failure.getMessage());
    }
}
