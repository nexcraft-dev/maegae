package dev.nexcraft.maegae.internal.json;

import java.math.BigDecimal;

/**
 * An immutable value in the internal JSON model.
 */
public sealed interface JsonValue
        permits JsonObject, JsonArray, JsonString, JsonNumber, JsonBoolean, JsonNull {

    static JsonString of(String value) {
        return JsonString.of(value);
    }

    static JsonNumber of(int value) {
        return JsonNumber.of(value);
    }

    static JsonNumber of(long value) {
        return JsonNumber.of(value);
    }

    static JsonNumber of(BigDecimal value) {
        return JsonNumber.of(value);
    }

    static JsonBoolean of(boolean value) {
        return JsonBoolean.of(value);
    }
}
