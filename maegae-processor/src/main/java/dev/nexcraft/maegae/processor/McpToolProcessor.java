package dev.nexcraft.maegae.processor;

import dev.nexcraft.maegae.annotation.Arg;
import dev.nexcraft.maegae.annotation.Tool;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.tools.Diagnostic;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Discovers and validates Tool declarations exposed by annotation processing.
 * Local and anonymous classes are outside the supported provider scope. Their Tool
 * annotations may not be discovered or rejected by {@link RoundEnvironment}.
 */
public final class McpToolProcessor extends AbstractProcessor {
    private final Set<ExecutableElement> processed = new HashSet<>();
    private final Map<TypeElement, Set<String>> providerNames = new HashMap<>();
    private final GeneratedNames generatedNames;

    /** Creates the processor used by the Java compiler service loader. */
    public McpToolProcessor() {
        this(new GeneratedNames());
    }

    McpToolProcessor(final GeneratedNames generatedNames) {
        this.generatedNames = generatedNames;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of("dev.nexcraft.maegae.annotation.Tool");
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.RELEASE_25;
    }

    @Override
    public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
        if (!roundEnv.processingOver()) {
            final Map<TypeElement, List<ToolDeclaration>> providers = new LinkedHashMap<>();
            final Set<TypeElement> invalidProviders = new HashSet<>();
            for (final Element element : roundEnv.getElementsAnnotatedWith(Tool.class)) {
                if (element instanceof ExecutableElement method && element.getKind() == ElementKind.METHOD) {
                    if (processed.add(method)) {
                        final TypeElement provider = (TypeElement) method.getEnclosingElement();
                        final List<ToolDeclaration> declarations = providers.computeIfAbsent(provider,
                                ignored -> new ArrayList<>());
                        final ToolDeclaration declaration = discover(method);
                        if (declaration == null) {
                            invalidProviders.add(provider);
                        } else {
                            declarations.add(declaration);
                        }
                    }
                } else {
                    error(element, "@Tool can only annotate a method.");
                }
            }
            final var generator = new ToolMetadataGenerator(processingEnv, generatedNames);
            providers.forEach((provider, declarations) -> {
                if (!invalidProviders.contains(provider)) {
                    generator.generate(provider, declarations);
                }
            });
        }
        return false;
    }

    private ToolDeclaration discover(final ExecutableElement method) {
        final TypeElement provider = (TypeElement) method.getEnclosingElement();
        final String name = effectiveName(method.getAnnotation(Tool.class).name(), method.getSimpleName().toString());
        boolean valid = validateMethod(method, provider);
        valid &= validateName(method, name, "Tool");
        if (!providerNames.computeIfAbsent(provider, ignored -> new HashSet<>()).add(name)) {
            error(method, "Duplicate MCP Tool name '" + name + "' in " + provider.getQualifiedName() + ".");
            valid = false;
        }
        final List<ArgumentDeclaration> arguments = new ArrayList<>();
        valid &= discoverArguments(method, arguments);
        return valid ? new ToolDeclaration(provider, method, name, List.copyOf(arguments)) : null;
    }

    private boolean validateMethod(final ExecutableElement method, final TypeElement provider) {
        boolean valid = true;
        final Set<Modifier> modifiers = method.getModifiers();
        if (!modifiers.contains(Modifier.PUBLIC)) {
            error(method, "@Tool method must be public.");
            valid = false;
        }
        if (modifiers.contains(Modifier.STATIC)) {
            error(method, "@Tool method must be an instance method, not static.");
            valid = false;
        }
        if (modifiers.contains(Modifier.ABSTRACT)) {
            error(method, "@Tool method must not be abstract.");
            valid = false;
        }
        if (!method.getTypeParameters().isEmpty()) {
            error(method, "@Tool method must not declare type parameters.");
            valid = false;
        }
        if (method.isVarArgs()) {
            error(method, "@Tool method must not use varargs.");
            valid = false;
        }
        if (!accessibleProvider(provider)) {
            error(method, "@Tool provider must be a class accessible from its package.");
            valid = false;
        }
        if (method.getReturnType().getKind() == TypeKind.ERROR) {
            error(method, "@Tool method return type could not be resolved.");
            valid = false;
        }
        return valid;
    }

    private boolean accessibleProvider(final TypeElement provider) {
        if (!provider.getKind().isClass()) {
            return false;
        }
        Element current = provider;
        while (current instanceof TypeElement type) {
            if (type.getModifiers().contains(Modifier.PRIVATE)
                    || type.getNestingKind() == NestingKind.LOCAL
                    || type.getNestingKind() == NestingKind.ANONYMOUS) {
                return false;
            }
            current = type.getEnclosingElement();
        }
        return true;
    }

    private boolean discoverArguments(final ExecutableElement method, final List<ArgumentDeclaration> arguments) {
        boolean valid = true;
        final Set<String> names = new HashSet<>();
        for (final VariableElement parameter : method.getParameters()) {
            final Arg annotation = parameter.getAnnotation(Arg.class);
            final String name = effectiveName(annotation == null ? "" : annotation.name(),
                    parameter.getSimpleName().toString());
            valid &= validateName(parameter, name, "argument");
            if (!names.add(name)) {
                error(parameter, "Duplicate MCP argument name '" + name + "' in method "
                        + method.getSimpleName() + ".");
                valid = false;
            }
            if (parameter.asType().getKind() == TypeKind.ERROR) {
                error(parameter, "@Tool argument type could not be resolved.");
                valid = false;
            }
            arguments.add(new ArgumentDeclaration(parameter, parameter.asType(), name,
                    annotation == null ? "" : annotation.description(), annotation == null || annotation.required()));
        }
        return valid;
    }

    private String effectiveName(final String explicit, final String fallback) {
        return explicit.isEmpty() ? fallback : explicit;
    }

    private boolean validateName(final Element element, final String name, final String kind) {
        if (name.isBlank()) {
            error(element, "MCP " + kind + " name must not be blank.");
            return false;
        }
        return true;
    }

    private void error(final Element element, final String message) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, element);
    }

}
