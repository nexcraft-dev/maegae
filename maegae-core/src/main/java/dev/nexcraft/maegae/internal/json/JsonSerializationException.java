package dev.nexcraft.maegae.internal.json;

/**
 * Indicates that a JSON value could not be serialized.
 */
public final class JsonSerializationException extends JsonException {

    public JsonSerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
