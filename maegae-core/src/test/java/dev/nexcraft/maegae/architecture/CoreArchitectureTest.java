package dev.nexcraft.maegae.architecture;

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

class CoreArchitectureTest {
    static JavaClasses productionClasses() {
        String output = Objects.requireNonNull(System.getProperty("maegae.core.mainClasses"),
                "Gradle must provide Core main class output paths");
        var directories = Arrays.stream(output.split(Pattern.quote(File.pathSeparator)))
                .map(Path::of).toList();
        return importProductionClasses(directories);
    }

    static JavaClasses importProductionClasses(List<Path> directories) {
        return new ClassFileImporter().importPaths(directories.stream().filter(Files::isDirectory).toList());
    }

    private static final JavaClasses PRODUCTION = productionClasses();

    @Test
    void coreIsIndependent() {
        CoreArchitectureRules.CORE_INDEPENDENCE.check(PRODUCTION);
    }

    @Test
    void publicApiEncapsulatesInternals() {
        CoreArchitectureRules.INTERNAL_ENCAPSULATION.check(PRODUCTION);
    }

    @Test
    void transportIsIndependent() {
        CoreArchitectureRules.TRANSPORT_INDEPENDENCE.check(PRODUCTION);
    }

    @Test
    void annotationsAreIndependent() {
        CoreArchitectureRules.ANNOTATION_INDEPENDENCE.check(PRODUCTION);
    }

    @Test
    void corePackagesAreAcyclic() {
        CoreArchitectureRules.NO_PACKAGE_CYCLES.check(PRODUCTION);
    }
}
