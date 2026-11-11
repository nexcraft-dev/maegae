package dev.nexcraft.maegae.processor;

import dev.nexcraft.maegae.annotation.Tool;

import javax.annotation.processing.Messager;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Renders one immutable metadata source file per validated provider. */
final class ToolMetadataGenerator {
    private static final int STRING_CHUNK_LENGTH = 8192;
    private final Messager messager;

    ToolMetadataGenerator(final Messager messager) {
        this.messager = messager;
    }

    /** Renders the metadata class, or reports errors and returns empty if a declaration is unsupported. */
    Optional<String> source(final String packageName, final String className,
                            final List<ToolDeclaration> declarations) {
        final var schemaGenerator = new ToolSchemaGenerator(messager);
        final List<Metadata> metadata = new ArrayList<>();
        for (final ToolDeclaration declaration : declarations) {
            schemaGenerator.schema(declaration).ifPresent(schema -> metadata.add(new Metadata(declaration, schema)));
        }
        return metadata.size() == declarations.size()
                ? Optional.of(render(packageName, className, metadata)) : Optional.empty();
    }

    private String render(final String packageName, final String className, final List<Metadata> metadata) {
        final StringBuilder result = new StringBuilder();
        if (!packageName.isEmpty()) {
            result.append("package ").append(packageName).append(";\n\n");
        }
        result.append("/** Compile-time Tool metadata. */\npublic final class ").append(className).append(" {\n")
                .append("    private ").append(className).append("() {}\n")
                .append("    /** Returns immutable Tool definitions. */\n")
                .append("    public static java.util.List<dev.nexcraft.maegae.protocol.mcp.ToolDefinition> tools() {\n")
                .append("        return java.util.List.of(");
        for (int index = 0; index < metadata.size(); index++) {
            if (index > 0) {
                result.append(',');
            }
            result.append("\n            ").append(definition(metadata.get(index)));
        }
        return result.append(");\n    }\n}\n").toString();
    }

    private String definition(final Metadata metadata) {
        final ToolDeclaration tool = metadata.declaration();
        final String description = tool.method().getAnnotation(Tool.class).description();
        final String optional = description.isEmpty() ? "java.util.Optional.empty()"
                : "java.util.Optional.of(" + JavaSourceText.string(description) + ")";
        return "new dev.nexcraft.maegae.protocol.mcp.ToolDefinition(" + JavaSourceText.string(tool.name()) + ", "
                + optional + ", " + schemaExpression(metadata.schema()) + ")";
    }

    private String schemaExpression(final String schema) {
        if (schema.length() <= STRING_CHUNK_LENGTH) {
            return JavaSourceText.string(schema);
        }
        final List<String> chunks = new ArrayList<>();
        for (int index = 0; index < schema.length(); index += STRING_CHUNK_LENGTH) {
            chunks.add(JavaSourceText.string(schema.substring(index, Math.min(index + STRING_CHUNK_LENGTH, schema.length()))));
        }
        // A method call prevents javac from folding the chunks into one oversized constant.
        return "java.lang.String.join(\"\", " + String.join(", ", chunks) + ")";
    }

    /** A validated declaration and its generated JSON Schema text. */
    private record Metadata(ToolDeclaration declaration, String schema) { }
}
