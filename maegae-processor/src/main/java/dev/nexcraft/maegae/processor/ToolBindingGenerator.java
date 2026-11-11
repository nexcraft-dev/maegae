package dev.nexcraft.maegae.processor;

import javax.lang.model.element.TypeElement;

/**
 * Renders the binding of one provider. The binding delegates to the generated metadata and invokers.
 * Generated code uses fully qualified names only, so no name in the provider's package can shadow them.
 */
final class ToolBindingGenerator {
    private static final String BINDING = "dev.nexcraft.maegae.protocol.mcp.ToolProviderBinding";
    private static final String DEFINITION = "dev.nexcraft.maegae.protocol.mcp.ToolDefinition";
    private static final String INVOKER = "dev.nexcraft.maegae.tool.ToolInvoker";

    String source(final String packageName, final String className, final TypeElement provider,
                  final String metadataClass, final String invokersClass) {
        final String providerType = JavaSourceText.providerType(provider);
        final StringBuilder result = new StringBuilder();
        if (!packageName.isEmpty()) {
            result.append("package ").append(packageName).append(";\n\n");
        }
        return result.append("/** Compile-time Tool binding. */\npublic final class ").append(className)
                .append(" implements ").append(BINDING).append(" {\n")
                .append("    /** Creates the binding. {@link java.util.ServiceLoader} calls this constructor. */\n")
                .append("    public ").append(className).append("() {}\n\n")
                .append("    @Override\n    public java.lang.Class<?> providerType() {\n        return ")
                .append(provider.getQualifiedName()).append(".class;\n    }\n\n")
                .append("    @Override\n    public java.util.List<").append(DEFINITION).append("> tools() {\n")
                .append("        return ").append(metadataClass).append(".tools();\n    }\n\n")
                .append("    @Override\n    public java.util.Map<java.lang.String, ").append(INVOKER)
                .append("> invokers(final java.lang.Object provider) {\n")
                .append("        java.util.Objects.requireNonNull(provider, \"provider\");\n")
                .append("        if (provider instanceof ").append(providerType).append(" typed) {\n")
                .append("            return ").append(invokersClass).append(".invokers(typed);\n        }\n")
                .append("        throw new java.lang.IllegalArgumentException(\"Expected a provider of type \" + ")
                .append(JavaSourceText.string(provider.getQualifiedName().toString()))
                .append(" + \", but got \" + provider.getClass().getName() + \".\");\n    }\n}\n")
                .toString();
    }
}
