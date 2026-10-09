package dev.nexcraft.maegae.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.dependencies.SliceAssignment;
import com.tngtech.archunit.library.dependencies.SliceIdentifier;

import java.lang.module.ModuleFinder;
import java.util.Set;
import java.util.stream.Collectors;

import static com.tngtech.archunit.core.domain.JavaModifier.PROTECTED;
import static com.tngtech.archunit.core.domain.JavaModifier.PUBLIC;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

final class CoreArchitectureRules {
    static final String BASE = "dev.nexcraft.maegae";
    private static final Set<String> JDK_PACKAGES = ModuleFinder.ofSystem().findAll().stream()
            .flatMap(module -> module.descriptor().packages().stream())
            .collect(Collectors.toUnmodifiableSet());

    static final ArchRule CORE_INDEPENDENCE = noClasses().should().dependOnClassesThat()
            .resideInAnyPackage(BASE + ".processor..", BASE + ".transport.netty..",
                    BASE + ".example..", "io.netty..")
            .as("Core must be independent of other Maegae modules and Netty")
            .allowEmptyShould(true);

    static final ArchRule INTERNAL_ENCAPSULATION = classes()
            .that(new DescribedPredicate<>("are externally accessible Core API types") {
                @Override
                public boolean test(JavaClass type) {
                    return inPackage(type, BASE) && !inPackage(type, BASE + ".internal")
                            && externallyAccessible(type);
                }
            })
            .should(new ArchCondition<>("not expose internal types in API signatures") {
                @Override
                public void check(JavaClass type, ConditionEvents events) {
                    type.getSuperclass().ifPresent(parent -> checkSignature(type.getName(), parent, events));
                    type.getInterfaces().forEach(parent -> checkSignature(type.getName(), parent, events));
                    type.getTypeParameters().forEach(parameter -> checkSignature(type.getName(), parameter, events));
                    // Inherited public/protected members are part of a subclass's API too.
                    type.getAllFields().stream().filter(field -> visible(field.getModifiers()))
                            .forEach(field -> field.getAllInvolvedRawTypes()
                                    .forEach(target -> checkInternal(field.getFullName(), target, events)));
                    type.getAllMethods().stream().filter(method -> visible(method.getModifiers()))
                            .forEach(method -> checkCodeUnit(method, events));
                    type.getConstructors().stream().filter(constructor -> visible(constructor.getModifiers()))
                            .forEach(constructor -> checkCodeUnit(constructor, events));
                }
            })
            .as("Core API/SPI signatures must encapsulate internal types")
            .allowEmptyShould(true);

    static final ArchRule TRANSPORT_INDEPENDENCE = classes().that().resideInAPackage(BASE + ".transport..")
            .should().onlyDependOnClassesThat(new DescribedPredicate<>("are JDK types or Core public contracts") {
                @Override
                public boolean test(JavaClass type) {
                    JavaClass component = type.getBaseComponentType();
                    if (jdkType(component)) {
                        return true;
                    }
                    boolean contractPackage = component.getPackageName().equals(BASE)
                            || inPackage(component, BASE + ".annotation")
                            || inPackage(component, BASE + ".protocol")
                            || inPackage(component, BASE + ".tool")
                            || inPackage(component, BASE + ".transport");
                    return contractPackage && !inPackage(component, BASE + ".transport.netty")
                            && externallyAccessible(component);
                }
            })
            .as("Transport must depend only on the JDK and Core public contracts")
            .allowEmptyShould(true);

    static final ArchRule ANNOTATION_INDEPENDENCE = classes().that().resideInAPackage(BASE + ".annotation..")
            .should().onlyDependOnClassesThat(new DescribedPredicate<>("are JDK or annotation package types") {
                @Override
                public boolean test(JavaClass type) {
                    JavaClass component = type.getBaseComponentType();
                    return jdkType(component) || inPackage(component, BASE + ".annotation");
                }
            })
            .as("Annotations must depend only on the JDK and their own package")
            .allowEmptyShould(true);

    static final ArchRule NO_PACKAGE_CYCLES = slices().assignedFrom(new SliceAssignment() {
        @Override
        public SliceIdentifier getIdentifierOf(JavaClass type) {
            if (type.getPackageName().equals(BASE)) {
                return SliceIdentifier.of("<bootstrap>");
            }
            if (!inPackage(type, BASE)) {
                return SliceIdentifier.ignore();
            }
            String relativePackage = type.getPackageName().substring(BASE.length() + 1);
            return SliceIdentifier.of(relativePackage.split("\\.", 2)[0]);
        }

        @Override
        public String getDescription() {
            return "Core bootstrap and first-level packages";
        }
    }).should().beFreeOfCycles()
            .as("Core packages must be free of cycles")
            .allowEmptyShould(true);

    private static void checkCodeUnit(JavaCodeUnit member, ConditionEvents events) {
        member.getAllInvolvedRawTypes().forEach(target -> checkInternal(member.getFullName(), target, events));
        member.getExceptionTypes().forEach(target -> checkInternal(member.getFullName(), target, events));
    }

    private static void checkSignature(String origin, JavaType signature, ConditionEvents events) {
        signature.getAllInvolvedRawTypes().forEach(target -> checkInternal(origin, target, events));
    }

    private static void checkInternal(String origin, JavaClass target, ConditionEvents events) {
        JavaClass component = target.getBaseComponentType();
        if (inPackage(component, BASE + ".internal")) {
            events.add(SimpleConditionEvent.violated(target,
                    origin + " exposes internal type " + component.getName()));
        }
    }

    private static boolean externallyAccessible(JavaClass type) {
        if (type.isAnonymousClass() || type.isLocalClass()) {
            return false;
        }
        if (type.isTopLevelClass()) {
            return type.getModifiers().contains(PUBLIC);
        }
        return visible(type.getModifiers())
                && type.getEnclosingClass().map(CoreArchitectureRules::externallyAccessible).orElse(false);
    }

    private static boolean visible(Set<JavaModifier> modifiers) {
        return modifiers.contains(PUBLIC) || modifiers.contains(PROTECTED);
    }

    private static boolean jdkType(JavaClass type) {
        return type.isPrimitive() || JDK_PACKAGES.contains(type.getPackageName());
    }

    private static boolean inPackage(JavaClass type, String packageName) {
        return type.getPackageName().equals(packageName) || type.getPackageName().startsWith(packageName + ".");
    }

    private CoreArchitectureRules() {
    }
}
