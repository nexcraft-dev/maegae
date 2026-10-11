package dev.nexcraft.maegae.transport.netty.architecture;

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

import java.util.Set;

import static com.tngtech.archunit.core.domain.JavaModifier.PROTECTED;
import static com.tngtech.archunit.core.domain.JavaModifier.PUBLIC;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

final class NettyArchitectureRules {
    static final String BASE = "dev.nexcraft.maegae.transport.netty";
    static final ArchRule MODULE_INDEPENDENCE = noClasses().should().dependOnClassesThat()
            .resideInAnyPackage("dev.nexcraft.maegae.processor..", "dev.nexcraft.maegae.example..",
                    "dev.nexcraft.maegae.internal..")
            .as("Netty Transport must be independent of Processor, Example, and Core internals")
            .allowEmptyShould(true);

    static final ArchRule INTERNAL_DIRECTION = noClasses().that().resideInAPackage(BASE + ".internal..")
            .should().dependOnClassesThat().resideInAPackage(BASE + ".http..")
            .as("Netty internal must not depend on http")
            .allowEmptyShould(true);

    static final ArchRule ENTRY_POINT_DIRECTION = noClasses().that().resideInAPackage(BASE + "..")
            .and().resideOutsideOfPackage(BASE)
            .should().dependOnClassesThat(new DescribedPredicate<>("are NettyTransport or its nested types") {
                @Override
                public boolean test(JavaClass type) {
                    JavaClass component = type.getBaseComponentType();
                    return component.getName().equals(BASE + ".NettyTransport")
                            || component.getName().startsWith(BASE + ".NettyTransport$");
                }
            })
            .as("Netty subordinate packages must not depend on NettyTransport")
            .allowEmptyShould(true);

    static final ArchRule INTERNAL_ENCAPSULATION = classes()
            .that(new DescribedPredicate<>("are externally accessible Netty API types") {
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
                                    .forEach(target -> checkInternal(type.getName() + " API member " + field.getFullName(), target, events)));
                    type.getAllMethods().stream().filter(method -> visible(method.getModifiers()))
                            .forEach(method -> checkCodeUnit(type.getName(), method, events));
                    type.getConstructors().stream().filter(constructor -> visible(constructor.getModifiers()))
                            .forEach(constructor -> checkCodeUnit(type.getName(), constructor, events));
                }
            })
            .as("Netty API signatures must encapsulate internal types")
            .allowEmptyShould(true);

    static final ArchRule NO_PACKAGE_CYCLES = slices().assignedFrom(new SliceAssignment() {
        @Override
        public SliceIdentifier getIdentifierOf(JavaClass type) {
            if (type.getPackageName().equals(BASE)) {
                return SliceIdentifier.of("<entry>");
            }
            if (!inPackage(type, BASE)) {
                return SliceIdentifier.ignore();
            }
            String relativePackage = type.getPackageName().substring(BASE.length() + 1);
            return SliceIdentifier.of(relativePackage.split("\\.", 2)[0]);
        }

        @Override
        public String getDescription() {
            return "Netty entry point and first-level packages";
        }
    }).should().beFreeOfCycles()
            .as("Netty packages must be free of cycles")
            .allowEmptyShould(true);

    private static void checkCodeUnit(String apiType, JavaCodeUnit member, ConditionEvents events) {
        member.getAllInvolvedRawTypes().forEach(target -> checkInternal(apiType + " API member " + member.getFullName(), target, events));
        member.getExceptionTypes().forEach(target -> checkInternal(apiType + " API member " + member.getFullName(), target, events));
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
                && type.getEnclosingClass().map(NettyArchitectureRules::externallyAccessible).orElse(false);
    }

    private static boolean visible(Set<JavaModifier> modifiers) {
        return modifiers.contains(PUBLIC) || modifiers.contains(PROTECTED);
    }

    private static boolean inPackage(JavaClass type, String packageName) {
        return type.getPackageName().equals(packageName) || type.getPackageName().startsWith(packageName + ".");
    }

    private NettyArchitectureRules() {
    }
}
