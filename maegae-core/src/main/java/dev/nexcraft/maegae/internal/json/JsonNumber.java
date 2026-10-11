package dev.nexcraft.maegae.internal.json;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * An immutable JSON number represented without floating-point conversion.
 *
 * @param value the number value
 */
public record JsonNumber(BigDecimal value) implements JsonValue {

    public JsonNumber {
        Objects.requireNonNull(value, "value");
    }

    public static JsonNumber of(int value) {
        return new JsonNumber(BigDecimal.valueOf(value));
    }

    public static JsonNumber of(long value) {
        return new JsonNumber(BigDecimal.valueOf(value));
    }

    public static JsonNumber of(BigDecimal value) {
        return new JsonNumber(Objects.requireNonNull(value, "value"));
    }
}
