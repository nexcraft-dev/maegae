package dev.nexcraft.maegae.protocol.mcp;

import dev.nexcraft.maegae.internal.json.JsonBoolean;
import dev.nexcraft.maegae.internal.json.JsonObject;

import java.util.List;
import java.util.Objects;

/** Immutable client declarations for MCP 2025-11-25, retaining opaque extensions. */
record ClientCapabilities(JsonObject values) {
    /** Validates supported declaration shapes without implementing their behavior. */
    ClientCapabilities {
        Objects.requireNonNull(values, "values");
        validateObjectFields(values, List.of("roots", "sampling", "elicitation", "experimental"));
        if (values.values().get("roots") instanceof JsonObject roots) {
            final var flag = roots.values().get("listChanged");
            if (flag != null && !(flag instanceof JsonBoolean)) {
                throw new IllegalArgumentException("roots.listChanged must be a boolean");
            }
        }
        if (values.values().get("sampling") instanceof JsonObject sampling) {
            validateObjectFields(sampling, List.of("context", "tools"));
        }
        if (values.values().get("elicitation") instanceof JsonObject elicitation) {
            validateObjectFields(elicitation, List.of("form", "url"));
        }
        if (values.values().get("experimental") instanceof JsonObject experimental) {
            validateObjectFields(experimental, List.copyOf(experimental.values().keySet()));
        }
    }

    /** Creates a declaration with no advertised client capabilities. */
    static ClientCapabilities empty() {
        return new ClientCapabilities(JsonObject.of());
    }

    /** Checks only present object-valued fields and preserves unknown fields. */
    private static void validateObjectFields(final JsonObject object, final List<String> fields) {
        for (final String field : fields) {
            final var value = object.values().get(field);
            if (value != null && !(value instanceof JsonObject)) {
                throw new IllegalArgumentException(field + " must be an object");
            }
        }
    }
}
