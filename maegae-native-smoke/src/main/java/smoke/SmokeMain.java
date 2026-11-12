package smoke;

import dev.nexcraft.maegae.internal.json.JacksonJsonCodec;
import dev.nexcraft.maegae.internal.json.JsonObject;
import dev.nexcraft.maegae.internal.tool.JsonToolArguments;
import dev.nexcraft.maegae.protocol.mcp.ToolDefinition;
import dev.nexcraft.maegae.runtime.ToolRegistry;
import dev.nexcraft.maegae.tool.ToolArgumentException;

import java.util.List;

/**
 * Smoke test of the Tool registry: binding discovery and Tool execution. The output is the same on the JVM and in a
 * native image. Providers are created with plain {@code new}. Nothing in this class uses reflection or
 * {@code ServiceLoader} directly.
 */
public final class SmokeMain {
    private static final List<String> EXPECTED_TOOLS =
            List.of("add", "boom", "describe", "greet", "kind", "subtract");
    private static final JacksonJsonCodec CODEC = new JacksonJsonCodec();
    private static boolean failed;

    private SmokeMain() {
    }

    public static void main(final String[] args) {
        final ToolRegistry[] holder = new ToolRegistry[1];
        step(1, () -> {
            holder[0] = ToolRegistry.of(List.of(new Box<Integer>(), new CalculatorTools(), new Providers.Greeter()));
            final List<String> names = holder[0].tools().stream().map(ToolDefinition::name).toList();
            System.out.println("STEP 1 tools=" + names.size() + " names=" + names);
            expect(1, "tools", EXPECTED_TOOLS.toString(), names.toString());
        });
        step(2, () -> {
            for (final ToolDefinition tool : holder[0].tools()) {
                System.out.println("STEP 2 schema " + tool.name() + " " + tool.inputSchemaJson());
            }
        });
        step(3, () -> {
            final ToolRegistry registry = holder[0];
            check(registry, "add", "{\"a\":2,\"b\":40}", "text 42");
            check(registry, "add", "{\"a\":1.0,\"b\":1}", "text 2");
            check(registry, "add", "{\"a\":1.5,\"b\":1}", "ToolArgumentException argument=a");
            check(registry, "add", "{\"b\":1}", "ToolArgumentException argument=a");
            check(registry, "subtract", "{\"a\":50,\"b\":8}", "text 42");
            check(registry, "describe", "{}", "text label=null");
            check(registry, "describe", "{\"label\":null}", "text label=null");
            check(registry, "describe", "{\"label\":\"x\"}", "text label=x");
            check(registry, "boom", "{}", "IllegalStateException message=boom");
            check(registry, "greet", "{\"name\":\"Maegae\"}", "text Hello, Maegae");
            check(registry, "kind", "{}", "text box");
        });
        step(4, () -> {
            String actual;
            try {
                ToolRegistry.of(List.of(new CalculatorTools(), new CalculatorTools()));
                actual = "no exception";
            } catch (IllegalArgumentException exception) {
                actual = exception.getClass().getName() + ": " + exception.getMessage();
            }
            System.out.println("STEP 4 duplicate -> " + actual);
            expect(4, "duplicate", "java.lang.IllegalArgumentException: Duplicate Tool name 'add' in providers "
                    + "smoke.CalculatorTools, smoke.CalculatorTools.", actual);
        });
        System.out.println(failed ? "RESULT FAIL" : "RESULT OK");
        System.exit(failed ? 1 : 0);
    }

    private static void check(final ToolRegistry registry, final String tool, final String json,
                              final String expected) {
        String actual;
        try {
            final var arguments = JsonToolArguments.of((JsonObject) CODEC.parse(json));
            actual = "text " + registry.find(tool).orElseThrow().invoke(arguments).text().orElse("<empty>");
        } catch (ToolArgumentException exception) {
            actual = "ToolArgumentException argument=" + exception.argumentName();
        } catch (Exception exception) {
            actual = exception.getClass().getSimpleName() + " message=" + exception.getMessage();
        }
        System.out.println("STEP 3 " + tool + " " + json + " -> " + actual);
        expect(3, tool + " " + json, expected, actual);
    }

    private static void expect(final int step, final String what, final String expected, final String actual) {
        if (!expected.equals(actual)) {
            failed = true;
            System.out.println("STEP " + step + " MISMATCH " + what + ": expected <" + expected + "> but was <"
                    + actual + ">");
        }
    }

    private static void step(final int number, final Runnable action) {
        try {
            action.run();
        } catch (Throwable throwable) {
            failed = true;
            System.out.println("STEP " + number + " FAIL: " + throwable.getClass().getName() + ": "
                    + throwable.getMessage());
        }
    }
}
