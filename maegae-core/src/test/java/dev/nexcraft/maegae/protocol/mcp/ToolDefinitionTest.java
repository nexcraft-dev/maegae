package dev.nexcraft.maegae.protocol.mcp;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ToolDefinitionTest {
    @Test
    void preservesMetadataAndRejectsNullComponents() {
        final var definition = new ToolDefinition("Tool.Name", Optional.of(" description "), "{\"type\":\"object\"}");
        assertEquals("Tool.Name", definition.name());
        assertEquals(Optional.of(" description "), definition.description());
        assertEquals("{\"type\":\"object\"}", definition.inputSchemaJson());
        assertThrows(NullPointerException.class, () -> new ToolDefinition(null, Optional.empty(), "{}"));
        assertThrows(NullPointerException.class, () -> new ToolDefinition("x", null, "{}"));
        assertThrows(NullPointerException.class, () -> new ToolDefinition("x", Optional.empty(), null));
    }
}
