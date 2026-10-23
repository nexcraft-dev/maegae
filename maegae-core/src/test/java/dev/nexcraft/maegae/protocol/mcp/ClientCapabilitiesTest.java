package dev.nexcraft.maegae.protocol.mcp;

import dev.nexcraft.maegae.internal.json.JsonArray;
import dev.nexcraft.maegae.internal.json.JsonNull;
import dev.nexcraft.maegae.internal.json.JsonObject;
import dev.nexcraft.maegae.internal.json.JsonValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClientCapabilitiesTest {
    @Test
    void preservesEmptyAndExplicitStandardDeclarations() {
        assertEquals(JsonObject.of(), ClientCapabilities.empty().values());
        final var values = JsonObject.of(
                "roots", JsonObject.of("listChanged", JsonValue.of(false)),
                "sampling", JsonObject.of("context", JsonObject.of(), "tools", JsonObject.of()),
                "elicitation", JsonObject.of("form", JsonObject.of(), "url", JsonObject.of()),
                "experimental", JsonObject.of("example", JsonObject.of("enabled", JsonValue.of(true))));

        assertEquals(values, new ClientCapabilities(values).values());
        assertEquals(JsonObject.of("roots", JsonObject.of()),
                new ClientCapabilities(JsonObject.of("roots", JsonObject.of())).values());
        assertEquals(JsonObject.of("roots", JsonObject.of("listChanged", JsonValue.of(true))),
                new ClientCapabilities(JsonObject.of("roots", JsonObject.of("listChanged", JsonValue.of(true))))
                        .values());
    }

    @Test
    void retainsExtensionSnapshotsAndPreventsMutationThroughAccessors() {
        final var items = new ArrayList<JsonValue>(List.of(JsonValue.of("original")));
        final var members = new LinkedHashMap<String, JsonValue>();
        members.put("example/extension", JsonObject.of("items", new JsonArray(items)));
        members.put("example/scalar", JsonValue.of("opaque"));
        final var capabilities = new ClientCapabilities(new JsonObject(members));

        items.clear();
        members.clear();

        assertEquals(JsonObject.of("example/extension",
                        JsonObject.of("items", JsonArray.of(JsonValue.of("original"))),
                        "example/scalar", JsonValue.of("opaque")), capabilities.values());
        assertThrows(UnsupportedOperationException.class, () -> capabilities.values().values().clear());
        final var extension = (JsonObject) capabilities.values().values().get("example/extension");
        assertThrows(UnsupportedOperationException.class, () -> extension.values().clear());
        final var retained = (JsonArray) extension.values().get("items");
        assertThrows(UnsupportedOperationException.class, () -> retained.values().clear());
    }

    @ParameterizedTest
    @MethodSource("invalidDeclarations")
    void rejectsInvalidSupportedDeclarationShapes(final JsonObject values) {
        assertThrows(IllegalArgumentException.class, () -> new ClientCapabilities(values));
    }

    static Stream<JsonObject> invalidDeclarations() {
        return Stream.of(
                JsonObject.of("roots", JsonNull.INSTANCE),
                JsonObject.of("sampling", JsonValue.of(false)),
                JsonObject.of("elicitation", JsonArray.of()),
                JsonObject.of("experimental", JsonValue.of("invalid")),
                JsonObject.of("roots", JsonObject.of("listChanged", JsonValue.of("false"))),
                JsonObject.of("roots", JsonObject.of("listChanged", JsonNull.INSTANCE)),
                JsonObject.of("sampling", JsonObject.of("context", JsonValue.of(true))),
                JsonObject.of("sampling", JsonObject.of("tools", JsonNull.INSTANCE)),
                JsonObject.of("elicitation", JsonObject.of("form", JsonArray.of())),
                JsonObject.of("elicitation", JsonObject.of("url", JsonValue.of(false))),
                JsonObject.of("experimental", JsonObject.of("example", JsonNull.INSTANCE)));
    }

    @Test
    void rejectsMissingCapabilityContainer() {
        assertThrows(NullPointerException.class, () -> new ClientCapabilities(null));
    }
}
