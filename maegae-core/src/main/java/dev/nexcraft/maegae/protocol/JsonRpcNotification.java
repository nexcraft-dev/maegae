package dev.nexcraft.maegae.protocol;

import dev.nexcraft.maegae.internal.json.JsonArray;
import dev.nexcraft.maegae.internal.json.JsonObject;
import dev.nexcraft.maegae.internal.json.JsonValue;

import java.util.Objects;
import java.util.Optional;

/** An immutable notification without an ID. */
record JsonRpcNotification(String method, Optional<JsonValue> params) implements JsonRpcMessage {
    JsonRpcNotification {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(params, "params");
        if (method.isEmpty()) {
            throw new IllegalArgumentException("Method must not be empty");
        }
        if (params.isPresent() && !(params.get() instanceof JsonObject || params.get() instanceof JsonArray)) {
            throw new IllegalArgumentException("Params must be a JSON object or array");
        }
    }

    JsonRpcNotification(String method) {
        this(method, Optional.empty());
    }
}
