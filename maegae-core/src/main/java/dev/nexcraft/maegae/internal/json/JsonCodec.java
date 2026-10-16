package dev.nexcraft.maegae.internal.json;

/**
 * Contract for converting between JSON text and the internal JSON value model.
 * Implementations must reject {@code null} inputs with {@link NullPointerException}.
 */
public interface JsonCodec {

    /**
     * Parses JSON text into a value.
     * Implementations must reject duplicate object property names at every nesting depth.
     * Resource-limit failures must report {@code true} from {@link JsonParseException#isResourceLimit()}.
     *
     * @param json JSON text
     * @return the parsed value
     * @throws NullPointerException if {@code json} is {@code null}
     * @throws JsonParseException if the input is malformed, contains duplicate property names,
     *                            or exceeds the implementation's resource limits
     */
    JsonValue parse(String json);

    /**
     * Serializes a value to JSON text.
     *
     * @param value the value to serialize
     * @return JSON text
     * @throws NullPointerException if {@code value} is {@code null}
     */
    String serialize(JsonValue value);
}
