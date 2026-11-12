package smoke;

import dev.nexcraft.maegae.internal.json.JacksonJsonCodec;
import dev.nexcraft.maegae.internal.json.JsonObject;
import dev.nexcraft.maegae.internal.tool.JsonToolArguments;
import dev.nexcraft.maegae.protocol.mcp.ToolDefinition;
import dev.nexcraft.maegae.protocol.mcp.ToolProviderBinding;
import dev.nexcraft.maegae.tool.ToolArgumentException;
import dev.nexcraft.maegae.tool.ToolInvoker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Smoke test of binding discovery and Tool execution. The output is the same on the JVM and in a native image.
 * Providers are created with plain {@code new}. Nothing in this class uses reflection.
 */
public final class SmokeMain {
    private static final List<String> EXPECTED_PROVIDERS =
            List.of("smoke.Box", "smoke.CalculatorTools", "smoke.Providers$Greeter");
    private static final JacksonJsonCodec CODEC = new JacksonJsonCodec();
    private static boolean failed;

    private SmokeMain() {
    }

    public static void main(final String[] args) {
        final List<ToolProviderBinding> bindings = new ArrayList<>();
        step(1, () -> {
            ServiceLoader.load(ToolProviderBinding.class).forEach(bindings::add);
            bindings.sort(Comparator.comparing(binding -> binding.providerType().getName()));
            final List<String> names = bindings.stream().map(binding -> binding.providerType().getName()).toList();
            System.out.println("STEP 1 bindings=" + bindings.size() + " providers=" + names);
            expect(1, "providers", EXPECTED_PROVIDERS.toString(), names.toString());
        });
        step(2, () -> {
            for (final ToolProviderBinding binding : bindings) {
                System.out.println("STEP 2 " + binding.providerType().getName() + " tools="
                        + binding.tools().stream().map(ToolDefinition::name).toList());
                for (final ToolDefinition tool : binding.tools()) {
                    System.out.println("STEP 2   schema " + tool.name() + " " + tool.inputSchemaJson());
                }
            }
        });
        step(3, () -> {
            final List<Object> providers = List.of(new Box<Integer>(), new CalculatorTools(), new Providers.Greeter());
            for (final Object provider : providers) {
                final ToolProviderBinding binding = bindings.stream()
                        .filter(candidate -> candidate.providerType() == provider.getClass()).findFirst()
                        .orElseThrow(() -> new IllegalStateException("No binding for " + provider.getClass().getName()));
                run(provider.getClass().getName(), binding.invokers(provider));
            }
        });
        System.out.println(failed ? "RESULT FAIL" : "RESULT OK");
        System.exit(failed ? 1 : 0);
    }

    private static void run(final String provider, final Map<String, ToolInvoker> invokers) {
        switch (provider) {
            case "smoke.CalculatorTools" -> {
                check(invokers, "add", "{\"a\":2,\"b\":40}", "text 42");
                check(invokers, "add", "{\"a\":1.0,\"b\":1}", "text 2");
                check(invokers, "add", "{\"a\":1.5,\"b\":1}", "ToolArgumentException argument=a");
                check(invokers, "add", "{\"b\":1}", "ToolArgumentException argument=a");
                check(invokers, "subtract", "{\"a\":50,\"b\":8}", "text 42");
                check(invokers, "describe", "{}", "text label=null");
                check(invokers, "describe", "{\"label\":null}", "text label=null");
                check(invokers, "describe", "{\"label\":\"x\"}", "text label=x");
                check(invokers, "boom", "{}", "IllegalStateException message=boom");
            }
            case "smoke.Providers$Greeter" -> check(invokers, "greet", "{\"name\":\"Maegae\"}", "text Hello, Maegae");
            case "smoke.Box" -> check(invokers, "kind", "{}", "text box");
            default -> throw new IllegalStateException("Unexpected provider " + provider);
        }
    }

    private static void check(final Map<String, ToolInvoker> invokers, final String tool, final String json,
                              final String expected) {
        String actual;
        try {
            final var arguments = JsonToolArguments.of((JsonObject) CODEC.parse(json));
            actual = "text " + invokers.get(tool).invoke(arguments).text().orElse("<empty>");
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
