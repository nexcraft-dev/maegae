package dev.nexcraft.maegae.transport;

public final class TransportFixtures {
    public interface Valid {
        dev.nexcraft.maegae.protocol.ProtocolFixtures.Message exchange(
                dev.nexcraft.maegae.tool.ToolFixtures.Contract tool,
                java.time.Duration timeout);
    }
    public interface ExternalNetwork {
        external.network.NetworkContract exchange();
    }
    public interface Netty {
        io.netty.fixture.NetworkFixture exchange();
    }
    public interface Internal {
        dev.nexcraft.maegae.internal.json.InternalFixtures.Value exchange();
    }
    static class HiddenContract { }
    public static class HiddenContractDependency {
        private HiddenContract contract;
    }
}
