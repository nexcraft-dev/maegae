package dev.nexcraft.maegae.tool;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ToolOutputTest {
    @Test
    void textAndEmptyAreDistinct() {
        assertEquals(Optional.of("value"), ToolOutput.text("value").text());
        assertEquals(Optional.of(""), ToolOutput.text("").text());
        assertEquals(Optional.empty(), ToolOutput.empty().text());
        assertNotEquals(ToolOutput.text(""), ToolOutput.empty());
        assertEquals(ToolOutput.text("a"), ToolOutput.text("a"));
        assertThrows(NullPointerException.class, () -> ToolOutput.text(null));
    }
}
