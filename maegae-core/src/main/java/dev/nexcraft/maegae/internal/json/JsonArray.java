package dev.nexcraft.maegae.internal.json;

import java.util.List;
import java.util.Objects;

/**
 * An immutable JSON array that preserves element order.
 *
 * @param values the array's elements
 */
public record JsonArray(List<JsonValue> values) implements JsonValue {

    public JsonArray {
        values = List.copyOf(Objects.requireNonNull(values, "values"));
    }

    /**
     * Creates an array from the supplied values.
     *
     * @param values the array's elements
     * @return an immutable JSON array
     * @throws NullPointerException if values or an element is {@code null}
     */
    public static JsonArray of(JsonValue... values) {
        Objects.requireNonNull(values, "values");
        return new JsonArray(List.of(values));
    }
}
