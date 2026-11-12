package dev.nexcraft.maegae.runtime;

import dev.nexcraft.maegae.protocol.mcp.ToolDefinition;
import dev.nexcraft.maegae.protocol.mcp.ToolProviderBinding;
import dev.nexcraft.maegae.tool.ToolInvoker;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Holds the Tools of a set of provider instances. For internal use by Maegae; there is no stability guarantee.
 *
 * <p>The registry finds the generated binding of each provider with {@link ServiceLoader}. The match is exact:
 * the binding class must equal {@code provider.getClass()}. The registry is immutable after creation and is safe
 * to share between threads. The registry does not make the provider instances thread-safe.</p>
 */
public final class ToolRegistry {
    private final List<ToolDefinition> tools;
    private final Map<String, ToolInvoker> invokers;

    private ToolRegistry(final List<ToolDefinition> tools, final Map<String, ToolInvoker> invokers) {
        this.tools = tools;
        this.invokers = invokers;
    }

    /**
     * Creates a registry. Bindings are found with {@code ServiceLoader.load(ToolProviderBinding.class)}, which
     * uses the thread context class loader.
     *
     * @param providers the provider instances; may be empty
     * @return the registry
     * @throws NullPointerException     if providers or an element is null
     * @throws IllegalStateException    if a provider has no binding, has more than one binding, or has an
     *                                  inconsistent binding
     * @throws IllegalArgumentException if two Tools have the same name
     */
    public static ToolRegistry of(final Collection<?> providers) {
        return create(ServiceLoader.load(ToolProviderBinding.class), providers);
    }

    static ToolRegistry create(final Iterable<ToolProviderBinding> bindings, final Collection<?> providers) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(providers, "providers");
        final List<Object> instances = new ArrayList<>();
        for (final Object provider : providers) {
            instances.add(Objects.requireNonNull(provider, "providers must not contain null"));
        }

        final Map<Class<?>, List<ToolProviderBinding>> byType = new HashMap<>();
        for (final ToolProviderBinding binding : bindings) {
            byType.computeIfAbsent(binding.providerType(), type -> new ArrayList<>()).add(binding);
        }

        final List<ToolDefinition> allTools = new ArrayList<>();
        final Map<String, ToolInvoker> allInvokers = new HashMap<>();
        final Map<String, List<String>> owners = new TreeMap<>();
        for (final Object provider : instances) {
            final Class<?> type = provider.getClass();
            final ToolProviderBinding binding = select(byType, type);
            final List<ToolDefinition> definitions = binding.tools();
            final Map<String, ToolInvoker> providerInvokers = binding.invokers(provider);
            checkConsistent(type, definitions, providerInvokers);
            for (final ToolDefinition definition : definitions) {
                owners.computeIfAbsent(definition.name(), name -> new ArrayList<>()).add(type.getName());
                allTools.add(definition);
            }
            allInvokers.putAll(providerInvokers);
        }
        for (final Map.Entry<String, List<String>> entry : owners.entrySet()) {
            if (entry.getValue().size() > 1) {
                final List<String> classes = new ArrayList<>(entry.getValue());
                classes.sort(Comparator.naturalOrder());
                throw new IllegalArgumentException("Duplicate Tool name '" + entry.getKey()
                        + "' in providers " + String.join(", ", classes) + ".");
            }
        }
        allTools.sort(Comparator.comparing(ToolDefinition::name));
        return new ToolRegistry(List.copyOf(allTools), Map.copyOf(allInvokers));
    }

    private static ToolProviderBinding select(final Map<Class<?>, List<ToolProviderBinding>> byType,
                                              final Class<?> type) {
        final List<ToolProviderBinding> candidates = byType.get(type);
        if (candidates == null) {
            throw new IllegalStateException("No Tool binding found for provider class " + type.getName()
                    + ". Add maegae-processor to annotationProcessor and rebuild. "
                    + "Subclasses and anonymous classes of a provider are not supported.");
        }
        if (candidates.size() > 1) {
            final TreeSet<String> names = new TreeSet<>();
            candidates.forEach(candidate -> names.add(candidate.getClass().getName()));
            throw new IllegalStateException("More than one Tool binding found for provider class "
                    + type.getName() + ": " + String.join(", ", names) + ".");
        }
        return candidates.get(0);
    }

    private static void checkConsistent(final Class<?> type, final List<ToolDefinition> definitions,
                                        final Map<String, ToolInvoker> providerInvokers) {
        final TreeSet<String> withoutInvoker = new TreeSet<>();
        definitions.forEach(definition -> withoutInvoker.add(definition.name()));
        final TreeSet<String> withoutTool = new TreeSet<>(providerInvokers.keySet());
        withoutTool.removeAll(withoutInvoker);
        withoutInvoker.removeAll(providerInvokers.keySet());
        if (!withoutInvoker.isEmpty() || !withoutTool.isEmpty()) {
            throw new IllegalStateException("Inconsistent Tool binding for provider class " + type.getName()
                    + ". Tools without invoker: " + withoutInvoker + ". Invokers without Tool: " + withoutTool + ".");
        }
    }

    /**
     * Returns the Tool definitions.
     *
     * @return an unmodifiable list, sorted by name with {@link String#compareTo(String)}
     */
    public List<ToolDefinition> tools() {
        return tools;
    }

    /**
     * Finds the invoker of a Tool.
     *
     * @param name the Tool name
     * @return the invoker, or an empty optional if the name is unknown
     * @throws NullPointerException if name is null
     */
    public Optional<ToolInvoker> find(final String name) {
        return Optional.ofNullable(invokers.get(Objects.requireNonNull(name, "name")));
    }
}
