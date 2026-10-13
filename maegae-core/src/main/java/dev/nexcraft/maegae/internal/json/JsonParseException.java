package dev.nexcraft.maegae.internal.json;

/**
 * Indicates that JSON text could not be parsed into a value.
 */
public final class JsonParseException extends JsonException {

    public JsonParseException(String message) {
        super(message);
    }

    public JsonParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
