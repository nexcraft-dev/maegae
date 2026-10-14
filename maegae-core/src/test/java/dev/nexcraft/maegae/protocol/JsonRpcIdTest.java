package dev.nexcraft.maegae.protocol;

import dev.nexcraft.maegae.internal.json.JsonArray;
import dev.nexcraft.maegae.internal.json.JsonNull;
import dev.nexcraft.maegae.internal.json.JsonNumber;
import dev.nexcraft.maegae.internal.json.JsonObject;
import dev.nexcraft.maegae.internal.json.JsonString;
import dev.nexcraft.maegae.internal.json.JsonValue;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonRpcIdTest {
    @Test
    void preservesStringIdsWithoutNormalizationOrNumericConversion() {
        String exact = "  request-123\n한글  ";
        assertEquals(new JsonString(exact), JsonRpcId.of(exact).value());
        assertEquals(new JsonString(""), JsonRpcId.of("").value());
        assertNotEquals(JsonRpcId.of("123"), JsonRpcId.of(123));
    }

    @Test
    void preservesIntegralLargeAndFractionalNumbersIncludingScale() {
        var large = new BigDecimal("123456789012345678901234567890");
        var fractional = new BigDecimal("12345678901234567890.12345678900");

        assertEquals(new JsonNumber(BigDecimal.valueOf(123)), JsonRpcId.of(123).value());
        assertEquals(new JsonNumber(BigDecimal.valueOf(Long.MAX_VALUE)), JsonRpcId.of(Long.MAX_VALUE).value());
        assertEquals(new JsonNumber(large), JsonRpcId.of(large).value());
        assertEquals(new JsonNumber(fractional), JsonRpcId.of(fractional).value());
        assertEquals(JsonRpcId.of(123), JsonRpcId.of(123L));
        assertEquals(JsonRpcId.of(large).hashCode(), JsonRpcId.of(new BigDecimal(large.toString())).hashCode());
        assertNotEquals(JsonRpcId.of(new BigDecimal("123.0")), JsonRpcId.of(123));
    }

    @Test
    void rejectsNonStringNonNumericAndJavaNullIds() {
        for (JsonValue invalid : List.of(JsonValue.of(true), JsonObject.of(), JsonArray.of(), JsonNull.INSTANCE)) {
            assertThrows(IllegalArgumentException.class, () -> new JsonRpcId(invalid));
        }
        assertThrows(NullPointerException.class, () -> new JsonRpcId(null));
        assertThrows(NullPointerException.class, () -> JsonRpcId.of((String) null));
        assertThrows(NullPointerException.class, () -> JsonRpcId.of((BigDecimal) null));
    }
}
