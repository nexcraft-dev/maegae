package dev.nexcraft.maegae.tool;

public final class ToolFixtures {
    public interface Contract { }
    public static class Cycle {
        private dev.nexcraft.maegae.protocol.ProtocolFixtures.Cycle message;
    }
    public static class BootstrapCycle {
        private dev.nexcraft.maegae.CoreFixtures.BootstrapCycle bootstrap;
    }
}
