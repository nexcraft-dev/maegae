package dev.nexcraft.maegae.protocol.mcp;

import dev.nexcraft.maegae.internal.json.JsonArray;
import dev.nexcraft.maegae.internal.json.JsonNull;
import dev.nexcraft.maegae.internal.json.JsonObject;
import dev.nexcraft.maegae.internal.json.JsonValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.LinkedHashMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServerCapabilitiesTest {
    @Test
    void defaultDeclaresOnlyToolsWithoutListChangeNotifications() {
        assertEquals(JsonObject.of("tools", JsonObject.of("listChanged", JsonValue.of(false))),
                ServerCapabilities.toolsOnly().values());
        assertEquals(JsonObject.of(), ServerCapabilities.empty().values());
    }

    @ParameterizedTest
    @MethodSource("toolsDeclarations")
    void preservesCapabilityAndFlagPresenceWithoutFillingDefaults(final JsonObject values) {
        assertEquals(values, new ServerCapabilities(values).values());
    }

    static Stream<JsonObject> toolsDeclarations() {
        return Stream.of(JsonObject.of(), JsonObject.of("tools", JsonObject.of()),
                JsonObject.of("tools", JsonObject.of("listChanged", JsonValue.of(false))),
                JsonObject.of("tools", JsonObject.of("listChanged", JsonValue.of(true))));
    }

    @Test
    void preservesExplicitExtensionsWithoutRuntimeSupportChecksOrMutableState() {
        final var tools = new LinkedHashMap<String, JsonValue>();
        tools.put("listChanged", JsonValue.of(true));
        final var members = new LinkedHashMap<String, JsonValue>();
        members.put("tools", new JsonObject(tools));
        members.put("experimental", JsonObject.of("example", JsonObject.of()));
        members.put("example/extension", JsonArray.of(JsonValue.of("opaque")));
        final var capabilities = new ServerCapabilities(new JsonObject(members));

        tools.clear();
        members.clear();

        assertEquals(JsonObject.of("tools", JsonObject.of("listChanged", JsonValue.of(true)),
                "experimental", JsonObject.of("example", JsonObject.of()),
                "example/extension", JsonArray.of(JsonValue.of("opaque"))), capabilities.values());
        assertThrows(UnsupportedOperationException.class, () -> capabilities.values().values().clear());
        final var retainedTools = (JsonObject) capabilities.values().values().get("tools");
        assertThrows(UnsupportedOperationException.class, () -> retainedTools.values().clear());
    }

    @ParameterizedTest
    @MethodSource("invalidDeclarations")
    void rejectsInvalidToolsAndExperimentalDeclarations(final JsonObject values) {
        assertThrows(IllegalArgumentException.class, () -> new ServerCapabilities(values));
    }

    static Stream<JsonObject> invalidDeclarations() {
        return Stream.of(JsonObject.of("tools", JsonNull.INSTANCE),
                JsonObject.of("tools", JsonValue.of(false)),
                JsonObject.of("tools", JsonObject.of("listChanged", JsonValue.of("true"))),
                JsonObject.of("tools", JsonObject.of("listChanged", JsonNull.INSTANCE)),
                JsonObject.of("experimental", JsonArray.of()),
                JsonObject.of("experimental", JsonObject.of("example", JsonValue.of(true))));
    }

    @Test
    void rejectsMissingCapabilityContainer() {
        assertThrows(NullPointerException.class, () -> new ServerCapabilities(null));
    }
}
