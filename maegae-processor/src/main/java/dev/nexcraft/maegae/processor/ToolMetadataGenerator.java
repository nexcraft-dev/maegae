package dev.nexcraft.maegae.processor;

import dev.nexcraft.maegae.annotation.Tool;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/** Generates one immutable metadata source file per validated provider. */
final class ToolMetadataGenerator {
    private static final int STRING_CHUNK_LENGTH = 8192;
    private final ProcessingEnvironment environment;

    ToolMetadataGenerator(final ProcessingEnvironment environment) {
        this.environment = environment;
    }

    void generate(final TypeElement provider, final List<ToolDeclaration> declarations) {
        final var schemaGenerator = new ToolSchemaGenerator(environment.getMessager());
        final List<Metadata> metadata = new ArrayList<>();
        for (final ToolDeclaration declaration : declarations.stream()
                .sorted(Comparator.comparing(ToolDeclaration::name)).toList()) {
            schemaGenerator.schema(declaration).ifPresent(schema -> metadata.add(new Metadata(declaration, schema)));
        }
        if (metadata.size() != declarations.size()) {
            return;
        }
        final String packageName = environment.getElementUtils().getPackageOf(provider).getQualifiedName().toString();
        final String binaryName = environment.getElementUtils().getBinaryName(provider).toString();
        final String className = "MaegaeToolMetadata_" + HexFormat.of().formatHex(
                binaryName.getBytes(StandardCharsets.UTF_8));
        final String qualifiedName = packageName.isEmpty() ? className : packageName + "." + className;
        try (final var writer = environment.getFiler().createSourceFile(qualifiedName, provider).openWriter()) {
            writer.write(source(packageName, className, metadata));
        } catch (IOException exception) {
            environment.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "Cannot generate Tool metadata '" + qualifiedName + "': " + exception.getMessage(), provider);
        }
    }

    private String source(final String packageName, final String className, final List<Metadata> metadata) {
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
                : "java.util.Optional.of(" + javaString(description) + ")";
        return "new dev.nexcraft.maegae.protocol.mcp.ToolDefinition(" + javaString(tool.name()) + ", "
                + optional + ", " + schemaExpression(metadata.schema()) + ")";
    }

    private String schemaExpression(final String schema) {
        if (schema.length() <= STRING_CHUNK_LENGTH) {
            return javaString(schema);
        }
        final List<String> chunks = new ArrayList<>();
        for (int index = 0; index < schema.length(); index += STRING_CHUNK_LENGTH) {
            chunks.add(javaString(schema.substring(index, Math.min(index + STRING_CHUNK_LENGTH, schema.length()))));
        }
        // A method call prevents javac from folding the chunks into one oversized constant.
        return "java.lang.String.join(\"\", " + String.join(", ", chunks) + ")";
    }

    private String javaString(final String value) {
        final StringBuilder result = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            final char character = value.charAt(index);
            if (character == '"' || character == '\\') {
                result.append('\\').append(character);
            } else if (character < 0x20 || character == 0x7f) {
                result.append(String.format("\\%03o", (int) character));
            } else if (character > 0x7e) {
                result.append(String.format("\\u%04x", (int) character));
            } else {
                result.append(character);
            }
        }
        return result.append('"').toString();
    }

    /** A validated declaration and its generated JSON Schema text. */
    private record Metadata(ToolDeclaration declaration, String schema) { }
}
