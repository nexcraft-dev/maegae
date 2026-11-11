package dev.nexcraft.maegae.processor;

import javax.annotation.processing.Messager;
import javax.tools.Diagnostic;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Maps supported Java parameters to JSON Schema object text. */
final class ToolSchemaGenerator {
    private final Messager messager;

    ToolSchemaGenerator(final Messager messager) {
        this.messager = messager;
    }

    Optional<String> schema(final ToolDeclaration tool) {
        final List<String> types = new ArrayList<>();
        boolean supported = true;
        for (final ArgumentDeclaration argument : tool.arguments()) {
            final String type = ToolType.of(argument.type()).map(ToolType::jsonType).orElse(null);
            if (type == null) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                        "Unsupported MCP Tool argument type '" + argument.type() + "' for parameter '"
                                + argument.parameter().getSimpleName() + "' in Tool '" + tool.name() + "'.",
                        argument.parameter());
                supported = false;
            }
            types.add(type);
        }
        return supported ? Optional.of(renderSchema(tool.arguments(), types)) : Optional.empty();
    }

    private String renderSchema(final List<ArgumentDeclaration> arguments, final List<String> types) {
        final StringBuilder json = new StringBuilder("{\"type\":\"object\",\"properties\":{");
        final List<String> required = new ArrayList<>();
        for (int index = 0; index < arguments.size(); index++) {
            final ArgumentDeclaration argument = arguments.get(index);
            if (index > 0) {
                json.append(',');
            }
            json.append(jsonString(argument.name())).append(":{\"type\":").append(jsonString(types.get(index)));
            if (!argument.description().isEmpty()) {
                json.append(",\"description\":").append(jsonString(argument.description()));
            }
            json.append('}');
            if (argument.required()) {
                required.add(jsonString(argument.name()));
            }
        }
        return json.append("},\"required\":[").append(String.join(",", required)).append("]}").toString();
    }

    private String jsonString(final String value) {
        final StringBuilder result = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            final char character = value.charAt(index);
            if (character == '"' || character == '\\') {
                result.append('\\').append(character);
            } else if (character < 0x20 || Character.isSurrogate(character)) {
                result.append(String.format("\\u%04x", (int) character));
            } else {
                result.append(character);
            }
        }
        return result.append('"').toString();
    }
}
