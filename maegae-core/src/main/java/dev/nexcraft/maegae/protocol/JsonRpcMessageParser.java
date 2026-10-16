package dev.nexcraft.maegae.protocol;

import dev.nexcraft.maegae.internal.json.JsonArray;
import dev.nexcraft.maegae.internal.json.JsonCodec;
import dev.nexcraft.maegae.internal.json.JsonNull;
import dev.nexcraft.maegae.internal.json.JsonNumber;
import dev.nexcraft.maegae.internal.json.JsonObject;
import dev.nexcraft.maegae.internal.json.JsonParseException;
import dev.nexcraft.maegae.internal.json.JsonString;
import dev.nexcraft.maegae.internal.json.JsonValue;

import java.util.Objects;
import java.util.Optional;

/** Parses one JSON-RPC 2.0 message using the internal JSON abstraction. */
final class JsonRpcMessageParser {
    private final JsonCodec codec;

    JsonRpcMessageParser(JsonCodec codec) {
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    /**
     * @throws NullPointerException if {@code json} is {@code null}
     * @throws JsonParseException if JSON parsing fails, including duplicate properties or resource limits
     * @throws JsonRpcValidationException if the document is not a valid JSON-RPC message
     */
    JsonRpcMessage parse(String json) {
        JsonValue value = codec.parse(json);
        if (!(value instanceof JsonObject object)) {
            throw new JsonRpcValidationException("JSON-RPC message must be a JSON object");
        }

        var fields = object.values();
        if (!(fields.get("jsonrpc") instanceof JsonString version)
                || !JsonRpcMessage.VERSION.equals(version.value())) {
            throw new JsonRpcValidationException("jsonrpc must be the string \"2.0\"");
        }

        boolean hasMethod = fields.containsKey("method");
        boolean hasResult = fields.containsKey("result");
        boolean hasError = fields.containsKey("error");
        if (hasMethod) {
            if (hasResult || hasError) {
                throw new JsonRpcValidationException("Request or notification must not contain result or error");
            }
            String method = parseMethod(fields.get("method"));
            Optional<JsonValue> params = parseParams(fields.get("params"));
            return fields.containsKey("id")
                    ? new JsonRpcRequest(parseId(fields.get("id")), method, params)
                    : new JsonRpcNotification(method, params);
        }

        if (hasResult == hasError) {
            throw new JsonRpcValidationException("Message must contain method or exactly one of result and error");
        }
        if (!fields.containsKey("id")) {
            throw new JsonRpcValidationException("Response must contain id");
        }

        JsonValue id = fields.get("id");
        if (hasResult) {
            return JsonRpcResponse.success(parseId(id), fields.get("result"));
        }
        JsonRpcError error = parseError(fields.get("error"));
        return id instanceof JsonNull
                ? JsonRpcResponse.protocolFailure(error)
                : JsonRpcResponse.failure(parseId(id), error);
    }

    private static String parseMethod(JsonValue value) {
        if (!(value instanceof JsonString method) || method.value().isEmpty()) {
            throw new JsonRpcValidationException("method must be a non-empty JSON string");
        }
        return method.value();
    }

    private static Optional<JsonValue> parseParams(JsonValue value) {
        if (value == null) {
            return Optional.empty();
        }
        if (!(value instanceof JsonObject || value instanceof JsonArray)) {
            throw new JsonRpcValidationException("params must be a JSON object or array");
        }
        return Optional.of(value);
    }

    private static JsonRpcId parseId(JsonValue value) {
        if (!(value instanceof JsonString || value instanceof JsonNumber)) {
            throw new JsonRpcValidationException("id must be a JSON string or number");
        }
        return new JsonRpcId(value);
    }

    private static JsonRpcError parseError(JsonValue value) {
        if (!(value instanceof JsonObject error)) {
            throw new JsonRpcValidationException("error must be a JSON object");
        }
        var fields = error.values();
        if (!(fields.get("code") instanceof JsonNumber code)) {
            throw new JsonRpcValidationException("error.code must be a JSON integer in the Java int range");
        }
        int errorCode;
        try {
            errorCode = code.value().intValueExact();
        } catch (ArithmeticException exception) {
            throw new JsonRpcValidationException("error.code must be a JSON integer in the Java int range", exception);
        }
        if (!(fields.get("message") instanceof JsonString message)) {
            throw new JsonRpcValidationException("error.message must be a JSON string");
        }
        return new JsonRpcError(errorCode, message.value(), Optional.ofNullable(fields.get("data")));
    }
}
