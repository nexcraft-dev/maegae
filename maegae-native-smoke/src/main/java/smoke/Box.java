package smoke;

import dev.nexcraft.maegae.annotation.Tool;

/** Generic provider. */
public class Box<T extends Number> {
    @Tool
    public String kind() {
        return "box";
    }
}
