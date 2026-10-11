package dev.nexcraft.maegae.transport.netty.http;

public final class HttpFixtures {
    public static class Valid {
        private dev.nexcraft.maegae.transport.netty.internal.InternalFixtures.Valid implementation;
        private io.netty.handler.codec.http.HttpRequest request;
        private java.net.URI endpoint;
        private dev.nexcraft.maegae.transport.TransportContractFixture contract;
    }
    public static class EntryDependency {
        private dev.nexcraft.maegae.transport.netty.NettyTransport entry;
    }
    public static class NestedEntryDependency {
        private dev.nexcraft.maegae.transport.netty.NettyTransport.Nested entry;
    }
    public static class OtherRootDependency {
        private dev.nexcraft.maegae.transport.netty.NettyFixtures.RootContract contract;
    }
    public static class Cycle {
        private dev.nexcraft.maegae.transport.netty.internal.InternalFixtures.Cycle implementation;
    }
}
