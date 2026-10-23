package dev.nexcraft.maegae.protocol.mcp;

import dev.nexcraft.maegae.internal.json.JsonBoolean;
import dev.nexcraft.maegae.internal.json.JsonObject;

import java.util.Objects;

/** Immutable server declarations, independent of runtime capability registration. */
record ServerCapabilities(JsonObject values) {
    /** Validates Tools and experimental declarations while retaining opaque extensions. */
    ServerCapabilities {
        Objects.requireNonNull(values, "values");
        final var tools = values.values().get("tools");
        if (tools != null) {
            if (!(tools instanceof JsonObject object)) {
                throw new IllegalArgumentException("tools must be an object");
            }
            final var flag = object.values().get("listChanged");
            if (flag != null && !(flag instanceof JsonBoolean)) {
                throw new IllegalArgumentException("tools.listChanged must be a boolean");
            }
        }
        final var experimental = values.values().get("experimental");
        if (experimental != null) {
            if (!(experimental instanceof JsonObject object)) {
                throw new IllegalArgumentException("experimental must be an object");
            }
            for (final var declaration : object.values().values()) {
                if (!(declaration instanceof JsonObject)) {
                    throw new IllegalArgumentException("Experimental declarations must be objects");
                }
            }
        }
    }

    /** Creates a declaration without advertised server capabilities. */
    static ServerCapabilities empty() {
        return new ServerCapabilities(JsonObject.of());
    }

    /** Declares the v1 Tools policy without enabling execution or change notifications. */
    static ServerCapabilities toolsOnly() {
        return new ServerCapabilities(JsonObject.of("tools", JsonObject.of("listChanged", JsonBoolean.of(false))));
    }
}
