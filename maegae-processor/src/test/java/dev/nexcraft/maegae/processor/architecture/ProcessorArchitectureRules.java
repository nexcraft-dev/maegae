package dev.nexcraft.maegae.processor.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SliceAssignment;
import com.tngtech.archunit.library.dependencies.SliceIdentifier;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

final class ProcessorArchitectureRules {
    private static final String BASE = "dev.nexcraft.maegae.processor";

    static final ArchRule MODULE_INDEPENDENCE = noClasses().should().dependOnClassesThat()
            .resideInAnyPackage("io.netty..", "dev.nexcraft.maegae.transport.netty..",
                    "dev.nexcraft.maegae.example..", "dev.nexcraft.maegae.internal..")
            .as("Processor must be independent of Netty, Example, and Core internals")
            .allowEmptyShould(true);

    static final ArchRule MODEL_DIRECTION = noClasses().that().resideInAPackage(BASE + ".model..")
            .should().dependOnClassesThat().resideInAnyPackage(BASE + ".schema..", BASE + ".generator..")
            .as("Processor model must not depend on schema or generator")
            .allowEmptyShould(true);

    static final ArchRule SCHEMA_DIRECTION = noClasses().that().resideInAPackage(BASE + ".schema..")
            .should().dependOnClassesThat().resideInAPackage(BASE + ".generator..")
            .as("Processor schema must not depend on generator")
            .allowEmptyShould(true);

    static final ArchRule ENTRY_POINT_DIRECTION = noClasses().that().resideInAPackage(BASE + "..")
            .and().resideOutsideOfPackage(BASE)
            .should().dependOnClassesThat().resideInAPackage(BASE)
            .as("Processor subordinate packages must not depend on the entry-point package")
            .allowEmptyShould(true);

    static final ArchRule NO_PACKAGE_CYCLES = slices().assignedFrom(new SliceAssignment() {
        @Override
        public SliceIdentifier getIdentifierOf(JavaClass type) {
            String packageName = type.getPackageName();
            if (packageName.equals(BASE)) {
                return SliceIdentifier.of("<entry>");
            }
            if (!packageName.startsWith(BASE + ".")) {
                return SliceIdentifier.ignore();
            }
            return SliceIdentifier.of(packageName.substring(BASE.length() + 1).split("\\.", 2)[0]);
        }

        @Override
        public String getDescription() {
            return "Processor entry point and first-level packages";
        }
    }).should().beFreeOfCycles()
            .as("Processor packages must be free of cycles")
            .allowEmptyShould(true);

    private ProcessorArchitectureRules() {
    }
}
