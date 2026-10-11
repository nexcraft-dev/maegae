package dev.nexcraft.maegae.internal.json;

import java.util.Objects;

/**
 * An immutable JSON string.
 *
 * @param value the string value
 */
public record JsonString(String value) implements JsonValue {

    public JsonString {
        Objects.requireNonNull(value, "value");
    }

    public static JsonString of(String value) {
        return new JsonString(Objects.requireNonNull(value, "value"));
    }
}
