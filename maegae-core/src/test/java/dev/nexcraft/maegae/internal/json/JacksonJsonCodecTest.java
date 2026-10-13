package dev.nexcraft.maegae.internal.json;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JacksonJsonCodecTest {

    private final JacksonJsonCodec codec = new JacksonJsonCodec();

    @Test
    void parsesNestedValuesAndExplicitJsonNull() {
        JsonValue value = codec.parse("""
                {"name":"Maegae","active":true,"items":[1,false,null,{"empty":[]}]}
                """);

        assertEquals(JsonObject.of(
                "name", JsonValue.of("Maegae"),
                "active", JsonValue.of(true),
                "items", JsonArray.of(
                        JsonValue.of(1),
                        JsonValue.of(false),
                        JsonNull.instance(),
                        JsonObject.of("empty", JsonArray.of()))), value);
        assertSame(JsonNull.instance(), codec.parse("null"));
    }

    @Test
    void preservesObjectOrderAndUsesLastValueForDuplicateNames() {
        JsonObject object = (JsonObject) codec.parse("{\"first\":1,\"second\":2,\"first\":3}");

        assertEquals(List.of("first", "second"), new ArrayList<>(object.values().keySet()));
        assertEquals(JsonValue.of(3), object.values().get("first"));
    }

    @Test
    void parsesNumbersAsExactBigDecimalsIncludingExponentForms() {
        JsonArray numbers = (JsonArray) codec.parse(
                "[123456789012345678901234567890.12345678901234567890,1.2300e+12,-4.5E-7]");

        assertEquals(new BigDecimal("123456789012345678901234567890.12345678901234567890"),
                ((JsonNumber) numbers.values().get(0)).value());
        assertEquals(new BigDecimal("1.2300e+12"), ((JsonNumber) numbers.values().get(1)).value());
        assertEquals(new BigDecimal("-4.5E-7"), ((JsonNumber) numbers.values().get(2)).value());
    }

    @Test
    void acceptsJsonWhitespaceAndRequiresExactlyOneDocument() {
        assertEquals(JsonArray.of(JsonValue.of(1), JsonValue.of(2)), codec.parse(" \n [1, 2]\t\r"));

        assertThrows(JsonParseException.class, () -> codec.parse("1 2"));
        assertThrows(JsonParseException.class, () -> codec.parse("{} true"));
        assertThrows(JsonParseException.class, () -> codec.parse("true trailing"));
    }

    @Test
    void rejectsEmptyMalformedAndNonStandardJson() {
        assertThrows(JsonParseException.class, () -> codec.parse(""));
        assertThrows(JsonParseException.class, () -> codec.parse("  \n\t"));
        assertThrows(JsonParseException.class, () -> codec.parse("{\"value\":}"));
        assertThrows(JsonParseException.class, () -> codec.parse("[1,"));
        assertThrows(JsonParseException.class, () -> codec.parse("// comment\n1"));
        assertThrows(JsonParseException.class, () -> codec.parse("{'single':'quotes'}"));
        assertThrows(JsonParseException.class, () -> codec.parse("{unquoted:1}"));
        assertThrows(JsonParseException.class, () -> codec.parse("[1,]"));
    }

    @Test
    void wrapsJacksonFailuresWithoutIncludingInputInOuterMessage() {
        String input = "{\"secret\":\"sensitive-value\",}";

        JsonParseException exception = assertThrows(JsonParseException.class, () -> codec.parse(input));

        assertTrue(exception.getMessage().contains("Malformed JSON input"));
        assertFalse(exception.getMessage().contains("sensitive-value"));
        assertNotNull(exception.getCause());
    }

    @Test
    void rejectsNullInput() {
        assertThrows(NullPointerException.class, () -> codec.parse(null));
    }

    @Test
    void serializesPrimitiveValuesAndEmptyContainers() {
        assertEquals("\"Maegae\"", codec.serialize(JsonValue.of("Maegae")));
        assertEquals("123", codec.serialize(JsonValue.of(123)));
        assertEquals("true", codec.serialize(JsonValue.of(true)));
        assertEquals("false", codec.serialize(JsonValue.of(false)));
        assertEquals("null", codec.serialize(JsonNull.instance()));
        assertEquals("{}", codec.serialize(JsonObject.of()));
        assertEquals("[]", codec.serialize(JsonArray.of()));
    }

    @Test
    void serializesNestedValuesPreservingObjectAndArrayOrder() {
        JsonValue value = JsonObject.of(
                "jsonrpc", JsonValue.of("2.0"),
                "method", JsonValue.of("tools/list"),
                "params", JsonArray.of(
                        JsonValue.of(1),
                        JsonObject.of("enabled", JsonValue.of(true), "result", JsonNull.instance())));

        String expected = "{\"jsonrpc\":\"2.0\",\"method\":\"tools/list\",\"params\":[1,{\"enabled\":true,\"result\":null}]}";

        assertEquals(expected, codec.serialize(value));
    }

    @Test
    void escapesJsonStringsAndPreservesUnicode() {
        String original = "quote \" slash \\ newline\n tab\t control\u0001 snowman ☃ emoji 😀";

        JsonString value = new JsonString(original);
        JsonObject object = JsonObject.of(original, value);

        assertEquals(value, codec.parse(codec.serialize(value)));
        assertEquals(object, codec.parse(codec.serialize(object)));
    }

    @Test
    void serializesBigDecimalsWithoutLosingPrecisionOrScale() {
        List<BigDecimal> numbers = List.of(
                new BigDecimal("123456789012345678901234567890"),
                new BigDecimal("-123"),
                new BigDecimal("123.456789012345678901234567890"),
                new BigDecimal("1E+10"),
                new BigDecimal("0.00000000000000000001"),
                new BigDecimal("1.2300"));
        JsonArray original = new JsonArray(numbers.stream().<JsonValue>map(JsonNumber::of).toList());

        JsonArray restored = (JsonArray) codec.parse(codec.serialize(original));

        for (int index = 0; index < numbers.size(); index++) {
            BigDecimal expected = numbers.get(index);
            BigDecimal actual = ((JsonNumber) restored.values().get(index)).value();
            assertEquals(0, expected.compareTo(actual));
        }
        assertEquals(numbers.get(5).scale(), ((JsonNumber) restored.values().get(5)).value().scale());
    }

    @Test
    void roundTripsParsedAndConstructedValues() {
        JsonValue parsed = codec.parse("{\"decimal\":1.2300,\"values\":[null,true,\"snowman ☃\"]}");
        JsonValue constructed = JsonObject.of(
                "large", JsonValue.of(new BigDecimal("123456789012345678901234567890.123456789")),
                "nested", JsonArray.of(JsonValue.of(false), JsonNull.instance()));

        assertEquals(parsed, codec.parse(codec.serialize(parsed)));
        assertEquals(constructed, codec.parse(codec.serialize(constructed)));
    }

    @Test
    void rejectsNullSerializationInput() {
        assertThrows(NullPointerException.class, () -> codec.serialize(null));
    }
}
