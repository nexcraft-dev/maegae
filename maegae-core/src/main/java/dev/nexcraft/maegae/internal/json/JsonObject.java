package dev.nexcraft.maegae.internal.json;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * An immutable JSON object that preserves entry iteration order.
 *
 * @param values the object's members
 */
public record JsonObject(Map<String, JsonValue> values) implements JsonValue {

    public JsonObject {
        Objects.requireNonNull(values, "values");
        var copy = new LinkedHashMap<String, JsonValue>(values.size());
        values.forEach((key, value) -> copy.put(
                Objects.requireNonNull(key, "object key"),
                Objects.requireNonNull(value, "object value")));
        values = Collections.unmodifiableMap(copy);
    }

    /**
     * Creates an object from alternating string keys and JSON values.
     * If a key occurs more than once, its last value is retained and its original position is preserved.
     *
     * @param entries alternating string keys and JSON values
     * @return an immutable JSON object
     * @throws IllegalArgumentException if the entries do not form key/value pairs
     *                                  or a key or value has an invalid type
     * @throws NullPointerException if entries, a key, or a value is {@code null}
     */
    public static JsonObject of(Object... entries) {
        Objects.requireNonNull(entries, "entries");
        if (entries.length % 2 != 0) {
            throw new IllegalArgumentException("Object entries must contain alternating keys and values");
        }

        var members = new LinkedHashMap<String, JsonValue>();
        for (int index = 0; index < entries.length; index += 2) {
            Object key = entries[index];
            Object value = entries[index + 1];
            if (key == null) {
                throw new NullPointerException("Object key at index " + index + " must not be null");
            }
            if (!(key instanceof String stringKey)) {
                throw new IllegalArgumentException("Object key at index " + index + " must be a String");
            }
            if (value == null) {
                throw new NullPointerException("Object value at index " + (index + 1) + " must not be null");
            }
            if (!(value instanceof JsonValue jsonValue)) {
                throw new IllegalArgumentException("Object value at index " + (index + 1) + " must be a JsonValue");
            }
            members.put(stringKey, jsonValue);
        }
        return new JsonObject(members);
    }
}
