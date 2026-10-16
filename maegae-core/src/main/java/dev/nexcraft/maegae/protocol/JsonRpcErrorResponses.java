package dev.nexcraft.maegae.protocol;

import dev.nexcraft.maegae.internal.json.JsonParseException;

import java.util.Objects;

/**
 * Standard error responses without client-visible diagnostic data.
 * Request-specific factories must only be used for requests, never for known notifications.
 * Parsing failure conversion does not suppress errors merely because input has no ID.
 */
final class JsonRpcErrorResponses {
    private static final JsonRpcError PARSE_ERROR = new JsonRpcError(JsonRpcError.PARSE_ERROR, "Parse error");
    private static final JsonRpcError INVALID_REQUEST = new JsonRpcError(JsonRpcError.INVALID_REQUEST, "Invalid Request");
    private static final JsonRpcError METHOD_NOT_FOUND = new JsonRpcError(JsonRpcError.METHOD_NOT_FOUND, "Method not found");
    private static final JsonRpcError INVALID_PARAMS = new JsonRpcError(JsonRpcError.INVALID_PARAMS, "Invalid params");
    private static final JsonRpcError INTERNAL_ERROR = new JsonRpcError(JsonRpcError.INTERNAL_ERROR, "Internal error");

    private JsonRpcErrorResponses() {
    }

    static JsonRpcResponse parseError() {
        return JsonRpcResponse.protocolFailure(PARSE_ERROR);
    }

    static JsonRpcResponse invalidRequest() {
        return JsonRpcResponse.protocolFailure(INVALID_REQUEST);
    }

    static JsonRpcResponse invalidRequest(JsonRpcId id) {
        return JsonRpcResponse.failure(id, INVALID_REQUEST);
    }

    static JsonRpcResponse methodNotFound(JsonRpcId id) {
        return JsonRpcResponse.failure(id, METHOD_NOT_FOUND);
    }

    static JsonRpcResponse invalidParams(JsonRpcId id) {
        return JsonRpcResponse.failure(id, INVALID_PARAMS);
    }

    static JsonRpcResponse internalError() {
        return JsonRpcResponse.protocolFailure(INTERNAL_ERROR);
    }

    static JsonRpcResponse internalError(JsonRpcId id) {
        return JsonRpcResponse.failure(id, INTERNAL_ERROR);
    }

    /** Converts a JSON parsing failure, rethrowing resource-limit failures unchanged. */
    static JsonRpcResponse fromParsingFailure(JsonParseException failure) {
        Objects.requireNonNull(failure, "failure");
        if (failure.isResourceLimit()) {
            throw failure;
        }
        return parseError();
    }

    /** Validation failures carry no reliable request ID, so conversion uses an undetermined ID. */
    static JsonRpcResponse fromValidationFailure(JsonRpcValidationException failure) {
        Objects.requireNonNull(failure, "failure");
        return invalidRequest();
    }
}
