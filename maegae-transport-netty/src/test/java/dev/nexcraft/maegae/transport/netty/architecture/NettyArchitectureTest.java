package dev.nexcraft.maegae.transport.netty.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

class NettyArchitectureTest {
    private static final JavaClasses PRODUCTION = productionClasses();

    private static JavaClasses productionClasses() {
        String output = Objects.requireNonNull(System.getProperty("maegae.netty.mainClasses"),
                "Gradle must provide Netty main class output paths");
        var directories = Arrays.stream(output.split(Pattern.quote(File.pathSeparator)))
                .map(Path::of).toList();
        return importProductionClasses(directories);
    }

    static JavaClasses importProductionClasses(List<Path> directories) {
        return new ClassFileImporter().importPaths(directories.stream().filter(Files::isDirectory).toList());
    }

    @Test
    void transportIsIndependent() {
        NettyArchitectureRules.MODULE_INDEPENDENCE.check(PRODUCTION);
    }

    @Test
    void internalDoesNotDependOnHttp() {
        NettyArchitectureRules.INTERNAL_DIRECTION.check(PRODUCTION);
    }

    @Test
    void subordinatePackagesDoNotDependOnNettyTransport() {
        NettyArchitectureRules.ENTRY_POINT_DIRECTION.check(PRODUCTION);
    }

    @Test
    void publicApisEncapsulateInternalTypes() {
        NettyArchitectureRules.INTERNAL_ENCAPSULATION.check(PRODUCTION);
    }

    @Test
    void packagesAreAcyclic() {
        NettyArchitectureRules.NO_PACKAGE_CYCLES.check(PRODUCTION);
    }
}
