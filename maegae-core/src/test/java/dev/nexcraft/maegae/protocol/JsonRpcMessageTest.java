package dev.nexcraft.maegae.protocol;

import dev.nexcraft.maegae.internal.json.JsonArray;
import dev.nexcraft.maegae.internal.json.JsonNull;
import dev.nexcraft.maegae.internal.json.JsonObject;
import dev.nexcraft.maegae.internal.json.JsonValue;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonRpcMessageTest {
    private static final JsonRpcId ID = JsonRpcId.of("request-123");

    @Test
    void constructsRequestsAndNotificationsWithAbsentOrStructuredParams() {
        String method = "  tools/call  ";
        var absentRequest = new JsonRpcRequest(ID, method);
        var absentNotification = new JsonRpcNotification(method);

        assertEquals(ID, absentRequest.id());
        assertEquals(method, absentRequest.method());
        assertEquals(method, absentNotification.method());
        assertEquals(Optional.empty(), absentRequest.params());
        assertEquals(Optional.empty(), absentNotification.params());

        for (JsonValue params : List.of(JsonObject.of(), JsonArray.of(),
                JsonObject.of("name", JsonValue.of("calculator")), JsonArray.of(JsonValue.of(1)))) {
            var request = new JsonRpcRequest(ID, method, Optional.of(params));
            var notification = new JsonRpcNotification(method, Optional.of(params));
            assertEquals(Optional.of(params), request.params());
            assertEquals(Optional.of(params), notification.params());
            assertNotEquals(absentRequest, request);
            assertNotEquals(absentNotification, notification);
        }
    }

    @Test
    void rejectsMissingIdsMethodsAndParamsContainersAndEmptyMethods() {
        assertThrows(NullPointerException.class, () -> new JsonRpcRequest(null, "method"));
        assertThrows(NullPointerException.class, () -> new JsonRpcRequest(ID, null));
        assertThrows(NullPointerException.class, () -> new JsonRpcNotification(null));
        assertThrows(IllegalArgumentException.class, () -> new JsonRpcRequest(ID, ""));
        assertThrows(IllegalArgumentException.class, () -> new JsonRpcNotification(""));
        assertThrows(NullPointerException.class, () -> new JsonRpcRequest(ID, "method", null));
        assertThrows(NullPointerException.class, () -> new JsonRpcNotification("method", null));
    }

    @Test
    void rejectsScalarAndExplicitJsonNullParamsForBothMessageTypes() {
        for (JsonValue invalid : List.of(JsonValue.of("value"), JsonValue.of(1),
                JsonValue.of(false), JsonNull.INSTANCE)) {
            assertThrows(IllegalArgumentException.class,
                    () -> new JsonRpcRequest(ID, "method", Optional.of(invalid)));
            assertThrows(IllegalArgumentException.class,
                    () -> new JsonRpcNotification("method", Optional.of(invalid)));
        }
    }

    @Test
    void preservesImmutableParamsAndStructuralMessageEquality() {
        var members = new LinkedHashMap<String, JsonValue>();
        members.put("name", JsonValue.of("original"));
        var elements = new ArrayList<JsonValue>(List.of(JsonValue.of(1)));
        var request = new JsonRpcRequest(ID, "method", Optional.of(new JsonObject(members)));
        var notification = new JsonRpcNotification("method", Optional.of(new JsonArray(elements)));

        members.put("name", JsonValue.of("changed"));
        elements.clear();

        var equalRequest = new JsonRpcRequest(JsonRpcId.of("request-123"), "method",
                Optional.of(JsonObject.of("name", JsonValue.of("original"))));
        var equalNotification = new JsonRpcNotification("method", Optional.of(JsonArray.of(JsonValue.of(1))));
        assertEquals(equalRequest, request);
        assertEquals(equalRequest.hashCode(), request.hashCode());
        assertEquals(equalNotification, notification);
        assertEquals(equalNotification.hashCode(), notification.hashCode());
        assertNotEquals(request, new JsonRpcRequest(JsonRpcId.of("other"), "method", request.params()));
        assertNotEquals(notification, new JsonRpcNotification("other", notification.params()));
    }

    @Test
    void allMessageVariantsReportJsonRpc20() {
        var error = new JsonRpcError(JsonRpcError.PARSE_ERROR, "Parse error");
        List<JsonRpcMessage> messages = List.of(new JsonRpcRequest(ID, "method"),
                new JsonRpcNotification("method"), JsonRpcResponse.success(ID, JsonNull.INSTANCE),
                JsonRpcResponse.failure(ID, error), JsonRpcResponse.protocolFailure(error));

        for (JsonRpcMessage message : messages) {
            assertEquals("2.0", message.jsonrpc());
        }
    }
}
