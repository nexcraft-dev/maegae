package dev.nexcraft.maegae.protocol.mcp;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable Tool metadata generated at compile time.
 *
 * <p>The schema is a JSON object encoded as text. A future MCP response serializer
 * must embed that object, rather than serialize this value as a JSON string.
 * Optional arguments do not yet define missing-value or primitive-null handling.</p>
 *
 * @param name the effective Tool name
 * @param description the optional Tool description
 * @param inputSchemaJson the input JSON Schema object encoded as JSON text
 */
public record ToolDefinition(String name, Optional<String> description, String inputSchemaJson) {
    /** Requires all metadata values and preserves their contents. */
    public ToolDefinition {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(inputSchemaJson, "inputSchemaJson");
    }
}
