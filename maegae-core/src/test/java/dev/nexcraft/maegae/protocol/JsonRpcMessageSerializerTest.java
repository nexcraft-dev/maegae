package dev.nexcraft.maegae.protocol;

import dev.nexcraft.maegae.internal.json.JacksonJsonCodec;
import dev.nexcraft.maegae.internal.json.JsonArray;
import dev.nexcraft.maegae.internal.json.JsonCodec;
import dev.nexcraft.maegae.internal.json.JsonNull;
import dev.nexcraft.maegae.internal.json.JsonObject;
import dev.nexcraft.maegae.internal.json.JsonSerializationException;
import dev.nexcraft.maegae.internal.json.JsonValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class JsonRpcMessageSerializerTest {
    private final JsonCodec codec = new JacksonJsonCodec();
    private final JsonRpcMessageSerializer serializer = new JsonRpcMessageSerializer(codec);
    private final JsonRpcMessageParser parser = new JsonRpcMessageParser(codec);

    @ParameterizedTest
    @MethodSource("messages")
    void serializesExactlyTheExpectedFieldsInOrderAndRoundTrips(JsonRpcMessage message, String expectedJson) {
        String json = serializer.serialize(message);
        JsonObject expected = parseObject(expectedJson);
        JsonObject actual = parseObject(json);

        assertEquals(expected, actual);
        assertEquals(List.copyOf(expected.values().keySet()), List.copyOf(actual.values().keySet()));
        if (actual.values().get("error") instanceof JsonObject actualError) {
            JsonObject expectedError = assertInstanceOf(JsonObject.class, expected.values().get("error"));
            assertEquals(List.copyOf(expectedError.values().keySet()), List.copyOf(actualError.values().keySet()));
        }
        assertEquals(message, parser.parse(json));
    }

    static Stream<Arguments> messages() {
        return Stream.of(
                arguments(new JsonRpcRequest(JsonRpcId.of("request-123"), "  tools/list  "), """
                        {"jsonrpc":"2.0","id":"request-123","method":"  tools/list  "}
                        """),
                arguments(new JsonRpcRequest(JsonRpcId.of(123), "tools/call", Optional.of(JsonObject.of(
                        "name", JsonValue.of("calculator"),
                        "arguments", JsonObject.of("a", JsonValue.of(10), "b", JsonValue.of(20))))), """
                        {"jsonrpc":"2.0","id":123,"method":"tools/call","params":{"name":"calculator","arguments":{"a":10,"b":20}}}
                        """),
                arguments(new JsonRpcRequest(JsonRpcId.of(123), "example", Optional.of(JsonArray.of(
                        JsonValue.of(1), JsonNull.INSTANCE, JsonObject.of("flag", JsonValue.of(true))))), """
                        {"jsonrpc":"2.0","id":123,"method":"example","params":[1,null,{"flag":true}]}
                        """),
                arguments(new JsonRpcNotification("notifications/initialized"), """
                        {"jsonrpc":"2.0","method":"notifications/initialized"}
                        """),
                arguments(new JsonRpcNotification("example", Optional.of(JsonObject.of())), """
                        {"jsonrpc":"2.0","method":"example","params":{}}
                        """),
                arguments(new JsonRpcNotification("example", Optional.of(JsonArray.of())), """
                        {"jsonrpc":"2.0","method":"example","params":[]}
                        """),
                arguments(JsonRpcResponse.success(JsonRpcId.of(123), JsonObject.of("tools", JsonArray.of())), """
                        {"jsonrpc":"2.0","id":123,"result":{"tools":[]}}
                        """),
                arguments(JsonRpcResponse.success(JsonRpcId.of("request-123"), JsonNull.INSTANCE), """
                        {"jsonrpc":"2.0","id":"request-123","result":null}
                        """),
                arguments(JsonRpcResponse.failure(JsonRpcId.of(123), new JsonRpcError(-32601, " Method not found ")), """
                        {"jsonrpc":"2.0","id":123,"error":{"code":-32601,"message":" Method not found "}}
                        """),
                arguments(JsonRpcResponse.protocolFailure(new JsonRpcError(-32600, "Invalid Request")), """
                        {"jsonrpc":"2.0","id":null,"error":{"code":-32600,"message":"Invalid Request"}}
                        """),
                arguments(JsonRpcResponse.failure(JsonRpcId.of(123),
                        new JsonRpcError(-1, "", Optional.of(JsonNull.INSTANCE))), """
                        {"jsonrpc":"2.0","id":123,"error":{"code":-1,"message":"","data":null}}
                        """));
    }

    @ParameterizedTest
    @MethodSource("ids")
    void preservesIdTypesPrecisionAndScaleAcrossRequestsAndResponses(JsonRpcId id) {
        for (JsonRpcMessage message : List.of(new JsonRpcRequest(id, "example"),
                JsonRpcResponse.success(id, JsonNull.INSTANCE),
                JsonRpcResponse.failure(id, new JsonRpcError(-1, "custom")))) {
            String json = serializer.serialize(message);

            assertEquals(id.value(), parseObject(json).values().get("id"));
            assertEquals(message, parser.parse(json));
        }
    }

    static Stream<JsonRpcId> ids() {
        return Stream.concat(
                Stream.of(JsonRpcId.of(""), JsonRpcId.of("123"), JsonRpcId.of("  request-123  ")),
                Stream.of("123", "123.5", "123456789012345678901234567890",
                                "12345678901234567890.12345678900", "1.2300e+12", "-4.5E-7", "0E+3")
                        .map(number -> JsonRpcId.of(new BigDecimal(number))));
    }

    @ParameterizedTest
    @MethodSource("payloads")
    void preservesEveryJsonKindInSuccessResultsAndPresentErrorData(JsonValue value) {
        JsonRpcId id = JsonRpcId.of(1);
        JsonObject success = parseObject(serializer.serialize(JsonRpcResponse.success(id, value)));
        JsonObject failure = parseObject(serializer.serialize(JsonRpcResponse.failure(id,
                new JsonRpcError(-1, "custom", Optional.of(value)))));
        JsonObject error = assertInstanceOf(JsonObject.class, failure.values().get("error"));

        assertEquals(value, success.values().get("result"));
        assertEquals(value, error.values().get("data"));
    }

    static Stream<JsonValue> payloads() {
        return Stream.of(JsonObject.of(), JsonArray.of(), JsonValue.of("detail"),
                JsonValue.of(new BigDecimal("42.500")), JsonValue.of(false), JsonNull.INSTANCE,
                JsonObject.of("nested", JsonArray.of(JsonNull.INSTANCE,
                        JsonObject.of("value", JsonValue.of(new BigDecimal("1.25"))))));
    }

    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -32601, 0, Integer.MAX_VALUE})
    void serializesStandardAndCustomErrorCodesAsJsonIntegers(int code) {
        JsonObject response = parseObject(serializer.serialize(
                JsonRpcResponse.failure(JsonRpcId.of(1), new JsonRpcError(code, "custom"))));
        JsonObject error = assertInstanceOf(JsonObject.class, response.values().get("error"));

        assertEquals(JsonValue.of(code), error.values().get("code"));
    }

    @Test
    void preservesEscapingAndUnicodeAcrossMessageStringsAndNestedPayloads() {
        String text = "  quote \" slash \\ newline\n tab\t control\u0001 한글 ☃ 😀  ";
        JsonRpcId id = JsonRpcId.of(text);
        JsonObject payload = JsonObject.of(text, JsonValue.of(text));

        for (JsonRpcMessage message : List.of(new JsonRpcRequest(id, text, Optional.of(payload)),
                new JsonRpcNotification(text, Optional.of(payload)), JsonRpcResponse.success(id, JsonValue.of(text)),
                JsonRpcResponse.failure(id, new JsonRpcError(-1, text, Optional.of(payload))))) {
            assertEquals(message, parser.parse(serializer.serialize(message)));
        }
    }

    @ParameterizedTest
    @MethodSource("depthBoundaries")
    void enforcesDepthIncludingTheMessageAndErrorObjects(JsonRpcMessage message, int depth) {
        var atLimit = new JsonRpcMessageSerializer(new JacksonJsonCodec(4096, depth));
        String json = atLimit.serialize(message);
        assertEquals(message, parser.parse(json));

        var belowLimit = new JsonRpcMessageSerializer(new JacksonJsonCodec(4096, depth - 1));
        JsonSerializationException exception = assertThrows(JsonSerializationException.class,
                () -> belowLimit.serialize(message));

        assertNotNull(exception.getCause());
        assertFalse(exception.getMessage().contains("sensitive-value"));
        assertFalse(exception.getMessage().contains(json));
    }

    static Stream<Arguments> depthBoundaries() {
        JsonObject params = JsonObject.of("items", JsonArray.of(JsonValue.of("sensitive-value")));
        return Stream.of(
                arguments(new JsonRpcRequest(JsonRpcId.of(1), "example", Optional.of(params)), 3),
                arguments(new JsonRpcNotification("example", Optional.of(params)), 3),
                arguments(JsonRpcResponse.success(JsonRpcId.of(1), JsonArray.of(JsonValue.of("sensitive-value"))), 2),
                arguments(JsonRpcResponse.protocolFailure(new JsonRpcError(-1, "custom",
                        Optional.of(JsonObject.of("detail", JsonValue.of("sensitive-value"))))), 3));
    }

    @Test
    void doesNotApplyTheCodecInputSizeLimitToSerializedOutput() {
        var limitedInputSerializer = new JsonRpcMessageSerializer(new JacksonJsonCodec(1, 64));
        var message = new JsonRpcRequest(JsonRpcId.of(1), "example",
                Optional.of(JsonObject.of("payload", JsonValue.of("output exceeds one byte"))));

        assertEquals(message, parser.parse(limitedInputSerializer.serialize(message)));
    }

    @Test
    void rejectsNullCodecAndJavaNullMessage() {
        assertThrows(NullPointerException.class, () -> new JsonRpcMessageSerializer(null));
        assertThrows(NullPointerException.class, () -> serializer.serialize(null));
    }

    private JsonObject parseObject(String json) {
        return assertInstanceOf(JsonObject.class, codec.parse(json));
    }
}
