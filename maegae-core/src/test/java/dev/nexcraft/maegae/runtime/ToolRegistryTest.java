package dev.nexcraft.maegae.runtime;

import dev.nexcraft.maegae.protocol.mcp.ToolDefinition;
import dev.nexcraft.maegae.protocol.mcp.ToolProviderBinding;
import dev.nexcraft.maegae.tool.ToolInvoker;
import dev.nexcraft.maegae.tool.ToolOutput;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Tests the registry with fake bindings. Real generated bindings are tested in maegae-processor. */
class ToolRegistryTest {
    static class ProviderA {
    }

    static final class ProviderB {
    }

    static final class Unbound {
    }

    static class FakeBinding implements ToolProviderBinding {
        private final Class<?> type;
        private final List<ToolDefinition> tools;
        private final Map<String, ToolInvoker> invokers;

        FakeBinding(Class<?> type, List<ToolDefinition> tools, Map<String, ToolInvoker> invokers) {
            this.type = type;
            this.tools = tools;
            this.invokers = invokers;
        }

        @Override
        public Class<?> providerType() {
            return type;
        }

        @Override
        public List<ToolDefinition> tools() {
            return tools;
        }

        @Override
        public Map<String, ToolInvoker> invokers(Object provider) {
            return invokers;
        }
    }

    private static ToolDefinition tool(String name) {
        return new ToolDefinition(name, Optional.empty(), "{}");
    }

    private static ToolProviderBinding binding(Class<?> type, String... names) {
        Map<String, ToolInvoker> invokers = new TreeMap<>();
        for (String name : names) {
            invokers.put(name, args -> ToolOutput.text(type.getSimpleName() + ":" + name));
        }
        return new FakeBinding(type, Arrays.stream(names).map(ToolRegistryTest::tool).toList(), invokers);
    }

    private static List<ToolProviderBinding> bindings(ToolProviderBinding... bindings) {
        return List.of(bindings);
    }

    @Test
    void sortsToolsByNameWhateverTheOrder() throws Exception {
        var bindings = bindings(binding(ProviderA.class, "zeta", "Alpha"), binding(ProviderB.class, "beta"));
        var forward = ToolRegistry.create(bindings, List.of(new ProviderA(), new ProviderB()));
        var reverse = ToolRegistry.create(bindings.reversed(), List.of(new ProviderB(), new ProviderA()));
        assertEquals(List.of("Alpha", "beta", "zeta"), forward.tools().stream().map(ToolDefinition::name).toList());
        assertEquals(forward.tools(), reverse.tools());
        assertEquals(ToolOutput.text("ProviderB:beta"), reverse.find("beta").orElseThrow().invoke(null));
        assertTrue(forward.find("missing").isEmpty());
    }

    @Test
    void duplicateNameAcrossProvidersNamesToolAndBothClassesInStableOrder() {
        var bindings = bindings(binding(ProviderA.class, "x"), binding(ProviderB.class, "x"));
        String message = assertThrows(IllegalArgumentException.class,
                () -> ToolRegistry.create(bindings, List.of(new ProviderB(), new ProviderA()))).getMessage();
        assertEquals("Duplicate Tool name 'x' in providers " + ProviderA.class.getName() + ", "
                + ProviderB.class.getName() + ".", message);
    }

    @Test
    void sameClassOrSameInstanceTwiceIsDuplicate() {
        var bindings = bindings(binding(ProviderA.class, "x"));
        var shared = new ProviderA();
        String expected = "Duplicate Tool name 'x' in providers " + ProviderA.class.getName() + ", "
                + ProviderA.class.getName() + ".";
        assertEquals(expected, assertThrows(IllegalArgumentException.class,
                () -> ToolRegistry.create(bindings, List.of(new ProviderA(), new ProviderA()))).getMessage());
        assertEquals(expected, assertThrows(IllegalArgumentException.class,
                () -> ToolRegistry.create(bindings, List.of(shared, shared))).getMessage());
    }

    @Test
    void reportsTheSmallestDuplicateNameWhateverTheOrder() {
        var bindings = bindings(binding(ProviderA.class, "b", "a"), binding(ProviderB.class, "a", "b"));
        var first = assertThrows(IllegalArgumentException.class,
                () -> ToolRegistry.create(bindings, List.of(new ProviderA(), new ProviderB())));
        var second = assertThrows(IllegalArgumentException.class,
                () -> ToolRegistry.create(bindings, List.of(new ProviderB(), new ProviderA())));
        assertEquals(first.getMessage(), second.getMessage());
        assertTrue(first.getMessage().contains("'a'"), first.getMessage());
    }

    @Test
    void missingBindingNamesClassAndRemedy() {
        String message = assertThrows(IllegalStateException.class,
                () -> ToolRegistry.create(bindings(binding(ProviderA.class, "x")), List.of(new Unbound()))).getMessage();
        assertEquals("No Tool binding found for provider class " + Unbound.class.getName()
                + ". Add maegae-processor to annotationProcessor and rebuild. "
                + "Subclasses and anonymous classes of a provider are not supported.", message);
    }

    @Test
    void subclassAndAnonymousSubclassDoNotMatchTheBindingOfTheirParent() {
        class Local extends ProviderA {
        }
        var bindings = bindings(binding(ProviderA.class, "x"));
        for (Object subclass : List.of(new Local(), new ProviderA() { })) {
            String message = assertThrows(IllegalStateException.class,
                    () -> ToolRegistry.create(bindings, List.of(subclass))).getMessage();
            assertTrue(message.contains(subclass.getClass().getName()), message);
            assertTrue(message.contains("maegae-processor"), message);
        }
    }

    @Test
    void twoBindingsForOneProviderTypeAreRejectedWithBothBindingNames() {
        var first = binding(ProviderA.class, "x");
        var second = new FakeBinding(ProviderA.class, List.of(), Map.of()) { };
        String message = assertThrows(IllegalStateException.class,
                () -> ToolRegistry.create(bindings(first, second), List.of(new ProviderA()))).getMessage();
        assertEquals("More than one Tool binding found for provider class " + ProviderA.class.getName() + ": "
                + Stream.of(first, second).map(b -> b.getClass().getName()).sorted().collect(Collectors.joining(", "))
                + ".", message);
    }

    @Test
    void toolNamesMustEqualInvokerNames() {
        var bindings = bindings(new FakeBinding(ProviderA.class, List.of(tool("a"), tool("b")),
                Map.of("b", args -> ToolOutput.empty(), "c", args -> ToolOutput.empty())));
        String message = assertThrows(IllegalStateException.class,
                () -> ToolRegistry.create(bindings, List.of(new ProviderA()))).getMessage();
        assertEquals("Inconsistent Tool binding for provider class " + ProviderA.class.getName()
                + ". Tools without invoker: [a]. Invokers without Tool: [c].", message);
    }

    @Test
    void invokerFailurePropagatesUnchanged() {
        var failure = new IllegalArgumentException("wrong provider");
        var throwing = new FakeBinding(ProviderA.class, List.of(tool("a")), Map.of("a", args -> ToolOutput.empty())) {
            @Override
            public Map<String, ToolInvoker> invokers(Object provider) {
                throw failure;
            }
        };
        assertSame(failure, assertThrows(IllegalArgumentException.class,
                () -> ToolRegistry.create(bindings(throwing), List.of(new ProviderA()))));
    }

    @Test
    void registryIsImmutableAndDetachedFromItsInput() {
        var bindings = bindings(binding(ProviderA.class, "a"));
        List<Object> input = new ArrayList<>(List.of(new ProviderA()));
        var registry = ToolRegistry.create(bindings, input);
        input.add(new Unbound());
        input.clear();
        assertEquals(1, registry.tools().size());
        assertTrue(registry.find("a").isPresent());
        assertThrows(UnsupportedOperationException.class, () -> registry.tools().add(tool("b")));
        assertThrows(UnsupportedOperationException.class, () -> registry.tools().clear());
    }

    @Test
    void emptyProvidersGiveEmptyRegistry() {
        var registry = ToolRegistry.create(bindings(binding(ProviderA.class, "a")), List.of());
        assertTrue(registry.tools().isEmpty());
        assertTrue(registry.find("a").isEmpty());
    }

    @Test
    void nullInputIsRejected() {
        var bindings = bindings(binding(ProviderA.class, "a"));
        assertEquals("providers", assertThrows(NullPointerException.class,
                () -> ToolRegistry.create(bindings, null)).getMessage());
        assertEquals("providers must not contain null", assertThrows(NullPointerException.class,
                () -> ToolRegistry.create(bindings, Arrays.asList(new ProviderA(), null))).getMessage());
        assertThrows(NullPointerException.class, () -> ToolRegistry.of(null));
        assertThrows(NullPointerException.class, () -> ToolRegistry.create(bindings, List.of()).find(null));
    }

    @Test
    void ofWithEmptyProvidersWorksWithoutAnyBinding() {
        assertTrue(ToolRegistry.of(List.of()).tools().isEmpty());
    }
}
