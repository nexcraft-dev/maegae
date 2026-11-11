package dev.nexcraft.maegae.tool;

/** Runs one Tool method of one provider instance. Generated code implements this contract. */
@FunctionalInterface
public interface ToolInvoker {
    /**
     * Converts the arguments, calls the Tool method, and converts its result.
     *
     * @param args the call arguments
     * @return the Tool output
     * @throws ToolArgumentException if an argument is invalid
     * @throws Exception any exception that the Tool method throws, unchanged
     */
    ToolOutput invoke(ToolArguments args) throws Exception;
}
