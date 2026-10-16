package dev.nexcraft.maegae.protocol;

import dev.nexcraft.maegae.internal.json.JsonCodec;
import dev.nexcraft.maegae.internal.json.JsonNull;
import dev.nexcraft.maegae.internal.json.JsonObject;
import dev.nexcraft.maegae.internal.json.JsonSerializationException;
import dev.nexcraft.maegae.internal.json.JsonValue;

import java.util.LinkedHashMap;
import java.util.Objects;

/** Serializes one JSON-RPC 2.0 message using the internal JSON abstraction. */
final class JsonRpcMessageSerializer {
    private final JsonCodec codec;

    JsonRpcMessageSerializer(JsonCodec codec) {
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    /**
     * @throws NullPointerException if {@code message} is {@code null}
     * @throws JsonSerializationException if JSON serialization fails, including the codec's depth limit
     */
    String serialize(JsonRpcMessage message) {
        Objects.requireNonNull(message, "message");

        var fields = new LinkedHashMap<String, JsonValue>();
        fields.put("jsonrpc", JsonValue.of(JsonRpcMessage.VERSION));
        switch (message) {
            case JsonRpcRequest request -> {
                fields.put("id", request.id().value());
                fields.put("method", JsonValue.of(request.method()));
                request.params().ifPresent(params -> fields.put("params", params));
            }
            case JsonRpcNotification notification -> {
                fields.put("method", JsonValue.of(notification.method()));
                notification.params().ifPresent(params -> fields.put("params", params));
            }
            case JsonRpcResponse response -> {
                if (response.result().isPresent()) {
                    fields.put("id", response.id().orElseThrow().value());
                    fields.put("result", response.result().orElseThrow());
                } else {
                    fields.put("id", response.id().map(JsonRpcId::value).orElse(JsonNull.INSTANCE));
                    fields.put("error", errorObject(response.error().orElseThrow()));
                }
            }
        }
        return codec.serialize(new JsonObject(fields));
    }

    private static JsonObject errorObject(JsonRpcError error) {
        var fields = new LinkedHashMap<String, JsonValue>();
        fields.put("code", JsonValue.of(error.code()));
        fields.put("message", JsonValue.of(error.message()));
        error.data().ifPresent(data -> fields.put("data", data));
        return new JsonObject(fields);
    }
}
