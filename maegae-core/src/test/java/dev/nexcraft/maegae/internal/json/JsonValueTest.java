package dev.nexcraft.maegae.internal.json;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonValueTest {

    @Test
    void scalarFactoriesPreserveValuesAndReuseSingletons() {
        var precise = new BigDecimal("12345678901234567890.12345678900");

        assertEquals(new JsonString("example"), JsonValue.of("example"));
        assertEquals(new JsonNumber(BigDecimal.valueOf(123)), JsonValue.of(123));
        assertEquals(new JsonNumber(BigDecimal.valueOf(123L)), JsonValue.of(123L));
        assertEquals(precise, ((JsonNumber) JsonValue.of(precise)).value());
        assertTrue(((JsonBoolean) JsonValue.of(true)).value());
        assertFalse(((JsonBoolean) JsonValue.of(false)).value());
        assertSame(JsonValue.of(true), JsonValue.of(true));
        assertSame(JsonValue.of(false), JsonValue.of(false));
        assertNotSame(JsonValue.of(true), JsonValue.of(false));
        assertSame(JsonNull.INSTANCE, JsonNull.instance());
    }

    @Test
    void objectCopiesInputPreservesOrderAndExposesReadOnlyMembers() {
        var source = new LinkedHashMap<String, JsonValue>();
        source.put("first", JsonValue.of(1));
        source.put("second", JsonValue.of("two"));
        var object = new JsonObject(source);

        source.put("first", JsonValue.of(9));
        source.remove("second");
        source.put("third", JsonNull.instance());

        assertEquals(List.of("first", "second"), new ArrayList<>(object.values().keySet()));
        assertEquals(JsonValue.of(1), object.values().get("first"));
        assertThrows(UnsupportedOperationException.class,
                () -> object.values().put("third", JsonNull.instance()));
    }

    @Test
    void objectFactoryValidatesPairsAndUsesLastDuplicateValue() {
        var object = JsonObject.of(
                "first", JsonValue.of(1),
                "second", JsonValue.of(2),
                "first", JsonValue.of(3));

        assertEquals(List.of("first", "second"), new ArrayList<>(object.values().keySet()));
        assertEquals(JsonValue.of(3), object.values().get("first"));
        assertThrows(IllegalArgumentException.class, () -> JsonObject.of("key"));
        assertThrows(IllegalArgumentException.class, () -> JsonObject.of(1, JsonValue.of(1)));
        assertThrows(IllegalArgumentException.class, () -> JsonObject.of("key", "not a JSON value"));
        assertThrows(NullPointerException.class, () -> JsonObject.of(null, JsonNull.instance()));
        assertThrows(NullPointerException.class, () -> JsonObject.of("key", null));
    }

    @Test
    void arrayCopiesInputPreservesOrderAndExposesReadOnlyElements() {
        var source = new ArrayList<JsonValue>(List.of(JsonValue.of("first"), JsonValue.of(2)));
        var array = new JsonArray(source);

        source.set(0, JsonValue.of("changed"));
        source.add(JsonNull.instance());

        assertEquals(List.of(JsonValue.of("first"), JsonValue.of(2)), array.values());
        assertThrows(UnsupportedOperationException.class, () -> array.values().add(JsonNull.instance()));
        assertEquals(List.of(JsonValue.of("a"), JsonValue.of("b")),
                JsonArray.of(JsonValue.of("a"), JsonValue.of("b")).values());
    }

    @Test
    void constructorsAndFactoriesRejectNullJavaReferences() {
        var nullKey = new LinkedHashMap<String, JsonValue>();
        nullKey.put(null, JsonValue.of(1));
        var nullValue = new LinkedHashMap<String, JsonValue>();
        nullValue.put("key", null);

        assertThrows(NullPointerException.class, () -> new JsonObject(null));
        assertThrows(NullPointerException.class, () -> new JsonObject(nullKey));
        assertThrows(NullPointerException.class, () -> new JsonObject(nullValue));
        assertThrows(NullPointerException.class, () -> new JsonArray(null));
        assertThrows(NullPointerException.class, () -> new JsonArray(Arrays.asList(JsonValue.of(1), null)));
        assertThrows(NullPointerException.class, () -> JsonArray.of((JsonValue[]) null));
        assertThrows(NullPointerException.class, () -> JsonArray.of((JsonValue) null));
        assertThrows(NullPointerException.class, () -> JsonString.of(null));
        assertThrows(NullPointerException.class, () -> new JsonString(null));
        assertThrows(NullPointerException.class, () -> JsonValue.of((String) null));
        assertThrows(NullPointerException.class, () -> JsonNumber.of((BigDecimal) null));
        assertThrows(NullPointerException.class, () -> new JsonNumber(null));
    }

    @Test
    void valueRecordsProvideStructuralEqualityAndHashCodes() {
        var object = JsonObject.of("answer", JsonValue.of(42));
        var equivalentObject = new JsonObject(Map.of("answer", JsonValue.of(42)));
        var array = JsonArray.of(JsonValue.of("x"), JsonValue.of(false));
        var equivalentArray = new JsonArray(List.of(JsonValue.of("x"), JsonValue.of(false)));

        assertEquals(object, equivalentObject);
        assertEquals(object.hashCode(), equivalentObject.hashCode());
        assertEquals(array, equivalentArray);
        assertEquals(array.hashCode(), equivalentArray.hashCode());
        assertFalse(array.equals(JsonArray.of(JsonValue.of(false), JsonValue.of("x"))));
    }
}
