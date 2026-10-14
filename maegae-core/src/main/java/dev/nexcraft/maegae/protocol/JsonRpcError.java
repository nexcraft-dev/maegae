package dev.nexcraft.maegae.protocol;

import dev.nexcraft.maegae.internal.json.JsonValue;

import java.util.Objects;
import java.util.Optional;

/** An immutable error; absent data and explicit JSON null data are distinct. */
record JsonRpcError(int code, String message, Optional<JsonValue> data) {
    static final int PARSE_ERROR = -32700;
    static final int INVALID_REQUEST = -32600;
    static final int METHOD_NOT_FOUND = -32601;
    static final int INVALID_PARAMS = -32602;
    static final int INTERNAL_ERROR = -32603;

    JsonRpcError {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(data, "data");
    }

    JsonRpcError(int code, String message) {
        this(code, message, Optional.empty());
    }
}
