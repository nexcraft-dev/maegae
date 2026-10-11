package dev.nexcraft.maegae.internal.json;

/**
 * Contract for converting between JSON text and the internal JSON value model.
 * Implementations must reject {@code null} inputs with {@link NullPointerException}.
 */
public interface JsonCodec {

    /**
     * Parses JSON text into a value.
     *
     * @param json JSON text
     * @return the parsed value
     * @throws NullPointerException if {@code json} is {@code null}
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
