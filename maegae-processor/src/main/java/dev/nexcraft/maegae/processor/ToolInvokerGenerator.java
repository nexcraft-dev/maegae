package dev.nexcraft.maegae.processor;

import javax.annotation.processing.Messager;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Renders the invocation adapter of one provider. The adapter calls Tool methods directly.
 * Generated code uses fully qualified names only, so no name in the provider's package can shadow them.
 */
final class ToolInvokerGenerator {
    private static final String TOOL_PACKAGE = "dev.nexcraft.maegae.tool.";
    private static final String OUTPUT = TOOL_PACKAGE + "ToolOutput";
    private static final String ARGUMENTS = TOOL_PACKAGE + "ToolArguments";
    private static final String INVOKER = TOOL_PACKAGE + "ToolInvoker";
    private static final String SUPPORTED_RETURN_TYPES = "void, String, boolean, byte, short, int, long, float, "
            + "double, their boxed types, BigInteger, and BigDecimal";

    private final Messager messager;

    ToolInvokerGenerator(final Messager messager) {
        this.messager = messager;
    }

    /** Renders the adapter class, or reports errors and returns empty if a declaration is unsupported. */
    Optional<String> source(final String packageName, final String className, final TypeElement provider,
                            final List<ToolDeclaration> declarations) {
        final List<Method> methods = new ArrayList<>();
        final Set<String> usedNames = new HashSet<>();
        boolean valid = true;
        for (final ToolDeclaration declaration : declarations) {
            final Optional<Method> method = method(declaration, usedNames);
            if (method.isPresent()) {
                methods.add(method.get());
            } else {
                valid = false;
            }
        }
        return valid ? Optional.of(render(packageName, className, provider, declarations, methods))
                : Optional.empty();
    }

    private Optional<Method> method(final ToolDeclaration tool, final Set<String> usedNames) {
        boolean valid = true;
        final List<String> argumentExpressions = new ArrayList<>();
        for (final ArgumentDeclaration argument : tool.arguments()) {
            final Optional<ToolType> type = ToolType.of(argument.type());
            if (type.isEmpty()) {
                // The schema generator reports unsupported argument types.
                valid = false;
            } else if (!argument.required() && argument.type().getKind().isPrimitive()) {
                error(argument, "Optional MCP Tool argument '" + argument.parameter().getSimpleName()
                        + "' in Tool '" + tool.name() + "' must not use primitive type '" + argument.type()
                        + "'. Use the boxed type or set required = true.");
                valid = false;
            } else {
                argumentExpressions.add(argumentExpression(argument, type.get()));
            }
        }
        final TypeMirror returnType = tool.method().getReturnType();
        final boolean isVoid = returnType.getKind() == TypeKind.VOID;
        final Optional<ToolType> result = ToolType.of(returnType);
        if (!isVoid && result.isEmpty()) {
            messager.printMessage(Diagnostic.Kind.ERROR, "Unsupported MCP Tool return type '" + returnType
                    + "' in Tool '" + tool.name() + "'. Supported return types: " + SUPPORTED_RETURN_TYPES + ".",
                    tool.method());
            valid = false;
        }
        if (!valid) {
            return Optional.empty();
        }
        return Optional.of(new Method(uniqueName(tool, usedNames), argumentExpressions, isVoid ? null : result.get(),
                !isVoid && returnType.getKind().isPrimitive()));
    }

    private String uniqueName(final ToolDeclaration tool, final Set<String> usedNames) {
        final String base = "invoke_" + tool.method().getSimpleName();
        String candidate = base;
        for (int number = 2; !usedNames.add(candidate); number++) {
            candidate = base + "_" + number;
        }
        return candidate;
    }

    private String argumentExpression(final ArgumentDeclaration argument, final ToolType type) {
        final String name = JavaSourceText.string(argument.name());
        final String call = "arguments." + type.getter() + "(" + name + ")";
        if (argument.required()) {
            return call;
        }
        final String value = type.boxedType() == null ? call : type.boxedType() + ".valueOf(" + call + ")";
        return "arguments.has(" + name + ") && !arguments.isNull(" + name + ") ? " + value + " : null";
    }

    private String render(final String packageName, final String className, final TypeElement provider,
                          final List<ToolDeclaration> declarations, final List<Method> methods) {
        final String providerType = provider.getQualifiedName()
                + (provider.getTypeParameters().isEmpty() ? "" : "<" + "?, ".repeat(
                provider.getTypeParameters().size() - 1) + "?>");
        final String mapType = "java.util.Map<java.lang.String, " + INVOKER + ">";
        final StringBuilder result = new StringBuilder();
        if (!packageName.isEmpty()) {
            result.append("package ").append(packageName).append(";\n\n");
        }
        result.append("/** Compile-time Tool invocation adapters. */\npublic final class ").append(className)
                .append(" {\n    private ").append(className).append("() {}\n\n")
                .append("    /** Returns immutable Tool invokers by Tool name. */\n")
                .append("    public static ").append(mapType).append(" invokers(final ").append(providerType)
                .append(" provider) {\n")
                .append("        java.util.Objects.requireNonNull(provider, \"provider\");\n")
                .append("        final java.util.LinkedHashMap<java.lang.String, ").append(INVOKER)
                .append("> invokers = new java.util.LinkedHashMap<>();\n");
        for (int index = 0; index < methods.size(); index++) {
            result.append("        invokers.put(").append(JavaSourceText.string(declarations.get(index).name()))
                    .append(", arguments -> ").append(methods.get(index).name()).append("(provider, arguments));\n");
        }
        result.append("        return java.util.Collections.unmodifiableMap(invokers);\n    }\n");
        for (int index = 0; index < methods.size(); index++) {
            result.append('\n').append(renderMethod(providerType, declarations.get(index), methods.get(index)));
        }
        return result.append("}\n").toString();
    }

    private String renderMethod(final String providerType, final ToolDeclaration tool, final Method method) {
        final StringBuilder result = new StringBuilder("    private static ").append(OUTPUT).append(' ')
                .append(method.name()).append("(final ").append(providerType).append(" provider, final ")
                .append(ARGUMENTS).append(" arguments) throws java.lang.Exception {\n");
        final List<String> names = new ArrayList<>();
        for (int index = 0; index < tool.arguments().size(); index++) {
            final ArgumentDeclaration argument = tool.arguments().get(index);
            result.append("        final ").append(argument.type()).append(" argument").append(index).append(" = ")
                    .append(method.arguments().get(index)).append(";\n");
            names.add("argument" + index);
        }
        final String call = "provider." + tool.method().getSimpleName() + "(" + String.join(", ", names) + ")";
        if (method.result() == null) {
            result.append("        ").append(call).append(";\n        return ").append(OUTPUT).append(".empty();\n");
        } else {
            result.append("        final ").append(tool.method().getReturnType()).append(" result = ")
                    .append(call).append(";\n");
            if (!method.primitiveResult()) {
                result.append("        if (result == null) {\n            return ").append(OUTPUT)
                        .append(".empty();\n        }\n");
            }
            final String text = method.result() == ToolType.STRING ? "result"
                    : "java.lang.String.valueOf(result)";
            result.append("        return ").append(OUTPUT).append(".text(").append(text).append(");\n");
        }
        return result.append("    }\n").toString();
    }

    private void error(final ArgumentDeclaration argument, final String message) {
        messager.printMessage(Diagnostic.Kind.ERROR, message, argument.parameter());
    }

    /** A generated method: its name, argument expressions, and result conversion. */
    private record Method(String name, List<String> arguments, ToolType result, boolean primitiveResult) { }
}
