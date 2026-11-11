package smoke;

import dev.nexcraft.maegae.annotation.Tool;

/** Holder of a public static nested provider. */
public final class Providers {
    private Providers() {
    }

    /** Public static nested provider. */
    public static class Greeter {
        @Tool
        public String greet(String name) {
            return "Hello, " + name;
        }
    }
}
