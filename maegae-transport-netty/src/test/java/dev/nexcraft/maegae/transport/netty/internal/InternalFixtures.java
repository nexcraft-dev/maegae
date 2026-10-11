package dev.nexcraft.maegae.transport.netty.internal;

public final class InternalFixtures {
    public static class Value { }
    public static class Failure extends Exception { }
    public interface Contract { }
    public static class HttpDependency {
        private dev.nexcraft.maegae.transport.netty.http.HttpFixtures.Valid http;
    }
    public static class Cycle {
        private dev.nexcraft.maegae.transport.netty.http.HttpFixtures.Cycle http;
    }
    public static class EntryCycle {
        private dev.nexcraft.maegae.transport.netty.NettyFixtures.RootCycle root;
    }
    public static class Valid {
        private io.netty.channel.EventLoop eventLoop;
        private dev.nexcraft.maegae.transport.TransportContractFixture contract;
    }
}
