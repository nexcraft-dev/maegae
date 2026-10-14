package dev.nexcraft.maegae.protocol;

import dev.nexcraft.maegae.internal.json.JsonValue;

import java.util.Objects;
import java.util.Optional;

/** An immutable success or error response, constructed only through explicit factories. */
final class JsonRpcResponse implements JsonRpcMessage {
    private final Optional<JsonRpcId> id;
    private final Optional<JsonValue> result;
    private final Optional<JsonRpcError> error;

    private JsonRpcResponse(Optional<JsonRpcId> id, Optional<JsonValue> result, Optional<JsonRpcError> error) {
        this.id = Objects.requireNonNull(id, "id");
        this.result = Objects.requireNonNull(result, "result");
        this.error = Objects.requireNonNull(error, "error");
        if (result.isPresent() == error.isPresent()) {
            throw new IllegalArgumentException("Response must contain exactly one of result and error");
        }
        if (result.isPresent() && id.isEmpty()) {
            throw new IllegalArgumentException("Success response must have an ID");
        }
    }

    static JsonRpcResponse success(JsonRpcId id, JsonValue result) {
        return new JsonRpcResponse(Optional.of(id), Optional.of(result), Optional.empty());
    }

    static JsonRpcResponse failure(JsonRpcId id, JsonRpcError error) {
        return new JsonRpcResponse(Optional.of(id), Optional.empty(), Optional.of(error));
    }

    /** Creates a protocol-level error response when the request ID cannot be determined. */
    static JsonRpcResponse protocolFailure(JsonRpcError error) {
        return new JsonRpcResponse(Optional.empty(), Optional.empty(), Optional.of(error));
    }

    Optional<JsonRpcId> id() {
        return id;
    }

    Optional<JsonValue> result() {
        return result;
    }

    Optional<JsonRpcError> error() {
        return error;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof JsonRpcResponse response
                && id.equals(response.id) && result.equals(response.result) && error.equals(response.error);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, result, error);
    }
}
