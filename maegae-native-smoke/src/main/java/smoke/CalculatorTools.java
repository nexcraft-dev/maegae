package smoke;

import dev.nexcraft.maegae.annotation.Arg;
import dev.nexcraft.maegae.annotation.Tool;

/** Top-level public provider. */
public class CalculatorTools {
    @Tool(description = "Adds two numbers")
    public int add(@Arg(description = "First") int a, @Arg(description = "Second") int b) {
        return a + b;
    }

    @Tool
    public int subtract(int a, int b) {
        return a - b;
    }

    @Tool
    public String describe(@Arg(required = false) String label) {
        return "label=" + label;
    }

    @Tool
    public String boom() {
        throw new IllegalStateException("boom");
    }
}
