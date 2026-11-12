package dev.nexcraft.maegae.runtime;

import dev.nexcraft.maegae.tool.ToolInvoker;

/** Architecture rule fixtures for the runtime package. */
public final class RuntimeFixtures {
    private RuntimeFixtures() {
    }

    public static class Inside {
    }

    public static class UsesCoreContracts {
        private ToolInvoker invoker;
    }

    public static class UsesReflection {
        private java.lang.reflect.Method method;
    }

    public static class UsesClassForName {
        Class<?> load() throws ClassNotFoundException {
            return Class.forName("java.lang.String");
        }
    }
}
