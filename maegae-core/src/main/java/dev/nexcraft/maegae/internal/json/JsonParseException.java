package dev.nexcraft.maegae.internal.json;

/**
 * Indicates that JSON text could not be parsed into a value.
 * Resource-limit failures remain parse exceptions and are identified by {@link #isResourceLimit()}.
 */
public final class JsonParseException extends JsonException {
    private final boolean resourceLimit;

    public JsonParseException(String message) {
        this(message, false);
    }

    public JsonParseException(String message, Throwable cause) {
        this(message, cause, false);
    }

    private JsonParseException(String message, boolean resourceLimit) {
        super(message);
        this.resourceLimit = resourceLimit;
    }

    private JsonParseException(String message, Throwable cause, boolean resourceLimit) {
        super(message, cause);
        this.resourceLimit = resourceLimit;
    }

    /** Creates a failure caused by an input resource limit. */
    public static JsonParseException resourceLimit(String message) {
        return new JsonParseException(message, true);
    }

    /** Creates a resource-limit failure while preserving its underlying cause. */
    public static JsonParseException resourceLimit(String message, Throwable cause) {
        return new JsonParseException(message, cause, true);
    }

    /** Returns whether parsing failed because an input resource limit was exceeded. */
    public boolean isResourceLimit() {
        return resourceLimit;
    }
}
