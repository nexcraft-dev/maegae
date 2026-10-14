package dev.nexcraft.maegae.protocol;

import dev.nexcraft.maegae.internal.json.JsonNumber;
import dev.nexcraft.maegae.internal.json.JsonString;
import dev.nexcraft.maegae.internal.json.JsonValue;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * A string or precise numeric ID. Prefer integer-valued numbers for generated requests;
 * fractional values are preserved without rounding. Numeric equality follows JsonNumber, including scale.
 */
record JsonRpcId(JsonValue value) {
    JsonRpcId {
        Objects.requireNonNull(value, "value");
        if (!(value instanceof JsonString || value instanceof JsonNumber)) {
            throw new IllegalArgumentException("ID must be a JSON string or number");
        }
    }

    static JsonRpcId of(String value) {
        return new JsonRpcId(JsonString.of(value));
    }

    static JsonRpcId of(int value) {
        return new JsonRpcId(JsonNumber.of(value));
    }

    static JsonRpcId of(long value) {
        return new JsonRpcId(JsonNumber.of(value));
    }

    static JsonRpcId of(BigDecimal value) {
        return new JsonRpcId(JsonNumber.of(value));
    }
}
