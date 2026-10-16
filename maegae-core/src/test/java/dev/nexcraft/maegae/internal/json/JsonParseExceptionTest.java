package dev.nexcraft.maegae.internal.json;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonParseExceptionTest {
    @Test
    void existingConstructorsRemainUnmarkedAndPreserveCauseInitialization() {
        var cause = new IllegalStateException("diagnostic");
        var withoutCause = new JsonParseException("invalid input");
        var withCause = new JsonParseException("invalid input", cause);

        assertFalse(withoutCause.isResourceLimit());
        assertFalse(withCause.isResourceLimit());
        assertSame(cause, withCause.getCause());
        assertSame(cause, withoutCause.initCause(cause).getCause());
    }

    @Test
    void resourceFactoriesMarkFailuresAndPreserveTheirCause() {
        var cause = new IllegalStateException("limit diagnostic");
        var withoutCause = JsonParseException.resourceLimit("input limit");
        var withCause = JsonParseException.resourceLimit("input limit", cause);

        assertTrue(withoutCause.isResourceLimit());
        assertTrue(withCause.isResourceLimit());
        assertSame(cause, withCause.getCause());
    }
}
