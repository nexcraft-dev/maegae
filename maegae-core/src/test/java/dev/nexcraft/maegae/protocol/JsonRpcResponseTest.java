package dev.nexcraft.maegae.protocol;

import dev.nexcraft.maegae.internal.json.JsonArray;
import dev.nexcraft.maegae.internal.json.JsonNull;
import dev.nexcraft.maegae.internal.json.JsonObject;
import dev.nexcraft.maegae.internal.json.JsonValue;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonRpcResponseTest {
    private static final JsonRpcId ID = JsonRpcId.of(new BigDecimal("12345678901234567890.00"));
    private static final JsonRpcError ERROR = new JsonRpcError(JsonRpcError.INVALID_REQUEST, "Invalid Request");

    @Test
    void factoriesPreserveIdsAndProduceExactlyOneResultOrError() {
        JsonValue result = JsonObject.of("answer", JsonValue.of(42));
        var success = JsonRpcResponse.success(ID, result);
        var failure = JsonRpcResponse.failure(ID, ERROR);

        assertEquals(Optional.of(ID), success.id());
        assertEquals(Optional.of(result), success.result());
        assertEquals(Optional.empty(), success.error());
        assertEquals(Optional.of(ID), failure.id());
        assertEquals(Optional.empty(), failure.result());
        assertEquals(Optional.of(ERROR), failure.error());
    }

    @Test
    void explicitJsonNullIsAPresentSuccessResult() {
        var response = JsonRpcResponse.success(ID, JsonNull.INSTANCE);

        assertEquals(Optional.of(ID), response.id());
        assertEquals(Optional.of(JsonNull.INSTANCE), response.result());
        assertEquals(Optional.empty(), response.error());
    }

    @Test
    void onlyProtocolFailureFactoryAllowsAnUndeterminedId() {
        var error = new JsonRpcError(JsonRpcError.PARSE_ERROR, "Parse error");
        var response = JsonRpcResponse.protocolFailure(error);

        assertEquals(Optional.empty(), response.id());
        assertEquals(Optional.empty(), response.result());
        assertEquals(Optional.of(error), response.error());
        assertThrows(NullPointerException.class, () -> JsonRpcResponse.success(null, JsonNull.INSTANCE));
        assertThrows(NullPointerException.class, () -> JsonRpcResponse.failure(null, error));
    }

    @Test
    void factoriesRejectMissingResultsAndErrors() {
        assertThrows(NullPointerException.class, () -> JsonRpcResponse.success(ID, null));
        assertThrows(NullPointerException.class, () -> JsonRpcResponse.failure(ID, null));
        assertThrows(NullPointerException.class, () -> JsonRpcResponse.protocolFailure(null));
    }

    @Test
    void standardErrorCodesAndCustomIntegerCodesArePreserved() {
        assertEquals(-32700, new JsonRpcError(JsonRpcError.PARSE_ERROR, "Parse error").code());
        assertEquals(-32600, ERROR.code());
        assertEquals(-32601, new JsonRpcError(JsonRpcError.METHOD_NOT_FOUND, "Method not found").code());
        assertEquals(-32602, new JsonRpcError(JsonRpcError.INVALID_PARAMS, "Invalid params").code());
        assertEquals(-32603, new JsonRpcError(JsonRpcError.INTERNAL_ERROR, "Internal error").code());
        assertEquals(Integer.MIN_VALUE, new JsonRpcError(Integer.MIN_VALUE, "").code());
        assertEquals(Integer.MAX_VALUE, new JsonRpcError(Integer.MAX_VALUE, "custom").code());
    }

    @Test
    void errorsRejectMissingMessagesAndDataContainersButAllowEmptyMessages() {
        assertThrows(NullPointerException.class, () -> new JsonRpcError(-1, null));
        assertThrows(NullPointerException.class, () -> new JsonRpcError(-1, "message", null));
        assertEquals("", new JsonRpcError(-1, "").message());
    }

    @Test
    void errorDataAcceptsEveryJsonKindAndDistinguishesAbsentFromExplicitNull() {
        var absent = new JsonRpcError(-1, "message");
        assertEquals(Optional.empty(), absent.data());

        for (JsonValue data : List.of(JsonObject.of(), JsonArray.of(), JsonValue.of("detail"),
                JsonValue.of(42), JsonValue.of(true), JsonNull.INSTANCE)) {
            var error = new JsonRpcError(-1, "message", Optional.of(data));
            assertEquals(Optional.of(data), error.data());
            assertNotEquals(absent, error);
        }
    }

    @Test
    void errorsAndResponsesHaveStructuralEqualityIncludingTheirVariantAndId() {
        var data = JsonObject.of("detail", JsonValue.of("original"));
        var error = new JsonRpcError(-1, "message", Optional.of(data));
        var equalError = new JsonRpcError(-1, "message",
                Optional.of(JsonObject.of("detail", JsonValue.of("original"))));
        assertEquals(error, equalError);
        assertEquals(error.hashCode(), equalError.hashCode());
        assertNotEquals(error, new JsonRpcError(-2, "message", error.data()));
        assertNotEquals(error, new JsonRpcError(-1, "other", error.data()));

        var success = JsonRpcResponse.success(ID, data);
        var equalSuccess = JsonRpcResponse.success(ID, JsonObject.of("detail", JsonValue.of("original")));
        var failure = JsonRpcResponse.failure(ID, error);
        var equalFailure = JsonRpcResponse.failure(ID, equalError);
        var protocolFailure = JsonRpcResponse.protocolFailure(error);
        var equalProtocolFailure = JsonRpcResponse.protocolFailure(equalError);
        assertEquals(success, equalSuccess);
        assertEquals(success.hashCode(), equalSuccess.hashCode());
        assertEquals(failure, equalFailure);
        assertEquals(failure.hashCode(), equalFailure.hashCode());
        assertEquals(protocolFailure, equalProtocolFailure);
        assertEquals(protocolFailure.hashCode(), equalProtocolFailure.hashCode());
        assertNotEquals(success, failure);
        assertNotEquals(failure, protocolFailure);
        assertNotEquals(success, JsonRpcResponse.success(JsonRpcId.of("other"), data));
        assertNotEquals(success, JsonRpcResponse.success(ID, JsonNull.INSTANCE));
        assertNotEquals(success, null);
        assertNotEquals(success, data);
    }
}
