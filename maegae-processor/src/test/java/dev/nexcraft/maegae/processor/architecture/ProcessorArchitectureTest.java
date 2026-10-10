package dev.nexcraft.maegae.processor.architecture;

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

class ProcessorArchitectureTest {
    private static final JavaClasses PRODUCTION = productionClasses();

    private static JavaClasses productionClasses() {
        String output = Objects.requireNonNull(System.getProperty("maegae.processor.mainClasses"),
                "Gradle must provide Processor main class output paths");
        var directories = Arrays.stream(output.split(Pattern.quote(File.pathSeparator)))
                .map(Path::of).toList();
        return importProductionClasses(directories);
    }

    static JavaClasses importProductionClasses(List<Path> directories) {
        return new ClassFileImporter().importPaths(directories.stream().filter(Files::isDirectory).toList());
    }

    @Test
    void processorIsIndependent() {
        ProcessorArchitectureRules.MODULE_INDEPENDENCE.check(PRODUCTION);
    }

    @Test
    void modelDoesNotDependOnSchemaOrGenerator() {
        ProcessorArchitectureRules.MODEL_DIRECTION.check(PRODUCTION);
    }

    @Test
    void schemaDoesNotDependOnGenerator() {
        ProcessorArchitectureRules.SCHEMA_DIRECTION.check(PRODUCTION);
    }

    @Test
    void subordinatePackagesDoNotDependOnEntryPoint() {
        ProcessorArchitectureRules.ENTRY_POINT_DIRECTION.check(PRODUCTION);
    }

    @Test
    void processorPackagesAreAcyclic() {
        ProcessorArchitectureRules.NO_PACKAGE_CYCLES.check(PRODUCTION);
    }
}
