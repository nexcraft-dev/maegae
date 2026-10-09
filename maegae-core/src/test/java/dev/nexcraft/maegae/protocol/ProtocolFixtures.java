package dev.nexcraft.maegae.protocol;

public final class ProtocolFixtures {
    public interface Contract<T> { }
    public static class Message { }
    public static class OneWay {
        private dev.nexcraft.maegae.tool.ToolFixtures.Contract contract;
    }
    public static class Cycle {
        private dev.nexcraft.maegae.tool.ToolFixtures.Cycle tool;
    }
}
