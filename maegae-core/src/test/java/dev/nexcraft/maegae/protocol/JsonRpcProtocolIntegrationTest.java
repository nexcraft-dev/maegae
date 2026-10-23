package dev.nexcraft.maegae.protocol;

import dev.nexcraft.maegae.internal.json.JacksonJsonCodec;
import dev.nexcraft.maegae.internal.json.JsonCodec;
import dev.nexcraft.maegae.internal.json.JsonParseException;
import dev.nexcraft.maegae.internal.json.JsonValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/** Exercises composed protocol paths from JSON input without dispatch or MCP mapping. */
class JsonRpcProtocolIntegrationTest {
    private final JsonCodec codec = new JacksonJsonCodec();
    private final JsonRpcMessageParser parser = new JsonRpcMessageParser(codec);
    private final JsonRpcMessageSerializer serializer = new JsonRpcMessageSerializer(codec);

    @ParameterizedTest
    @MethodSource("wireMessages")
    void preservesMessageSemanticsFromJsonThroughTheCompletePipeline(
            final String input, final Class<? extends JsonRpcMessage> expectedType) {
        final var message = parser.parse(input);
        assertInstanceOf(expectedType, message);

        final String output = serializer.serialize(message);

        assertEquals(codec.parse(input), codec.parse(output));
        assertEquals(message, parser.parse(output));
    }

    static Stream<Arguments> wireMessages() {
        return Stream.of(
                arguments("""
                        {"params":{"name":"한글 ☃","items":[1,true,null]},
                         "method":" vendor/custom ","id":" request-1 ","jsonrpc":"2.0"}
                        """, JsonRpcRequest.class),
                arguments("""
                        {"id":12345678901234567890.12345678900,"jsonrpc":"2.0","method":"example"}
                        """, JsonRpcRequest.class),
                arguments("""
                        {"params":[{"items":[false,null]}],"method":"vendor/notify","jsonrpc":"2.0"}
                        """, JsonRpcNotification.class),
                arguments("""
                        {"result":{"items":[null,{"amount":42.500}]},"jsonrpc":"2.0","id":9007199254740993}
                        """, JsonRpcResponse.class),
                arguments("""
                        {"error":{"data":{"items":["detail",null]},"message":" custom ","code":-1},
                         "id":"error-1","jsonrpc":"2.0"}
                        """, JsonRpcResponse.class),
                arguments("""
                        {"error":{"message":"Parse error","code":-32700},"id":null,"jsonrpc":"2.0"}
                        """, JsonRpcResponse.class),
                arguments("""
                        {"error":{"data":null,"message":"custom","code":-1},"id":1.2500,"jsonrpc":"2.0"}
                        """, JsonRpcResponse.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"jsonrpc\":\"2.0\",\"method\":\"sensitive-marker\",}",
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"id\":2,\"method\":\"sensitive-marker\"}"
    })
    void convertsActualSyntaxAndDuplicateFailuresToSanitizedWireErrors(final String input) {
        final var failure = assertThrows(JsonParseException.class, () -> parser.parse(input));
        assertFalse(failure.isResourceLimit());

        assertWireError(JsonRpcErrorResponses.fromParsingFailure(failure), """
                {"jsonrpc":"2.0","id":null,"error":{"code":-32700,"message":"Parse error"}}
                """);
    }

    @Test
    void convertsInvalidStructureWithoutTreatingItAsANotificationOrRecoveringAnUnrelatedId() {
        final String input = """
                {"jsonrpc":"2.0","id":true,"method":"sensitive-marker","custom":{"id":123}}
                """;
        final var failure = assertThrows(JsonRpcValidationException.class, () -> parser.parse(input));

        assertWireError(JsonRpcErrorResponses.fromValidationFailure(failure), """
                {"jsonrpc":"2.0","id":null,"error":{"code":-32600,"message":"Invalid Request"}}
                """);
    }

    @Test
    void propagatesUnexpectedCodecFailuresWithoutMisclassifyingThemAsClientErrors() {
        final var failure = new IllegalStateException("internal-diagnostic-marker");
        final JsonCodec failingCodec = new JsonCodec() {
            @Override
            public JsonValue parse(final String json) {
                throw failure;
            }

            @Override
            public String serialize(final JsonValue value) {
                throw failure;
            }
        };

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> new JsonRpcMessageParser(failingCodec).parse("{\"jsonrpc\":\"2.0\",\"method\":\"m\"}")));
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> new JsonRpcMessageSerializer(failingCodec).serialize(new JsonRpcNotification("m"))));
    }

    /** Requires the complete standard envelope, excluding extra fields and diagnostic data. */
    private void assertWireError(final JsonRpcResponse response, final String expectedJson) {
        final String output = serializer.serialize(response);

        assertEquals(codec.parse(expectedJson), codec.parse(output));
        assertEquals(response, parser.parse(output));
    }
}
