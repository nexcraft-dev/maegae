package dev.nexcraft.maegae.protocol;

/**
 * An internal JSON-RPC wire message. Package visibility keeps internal JSON values out of the public API.
 */
sealed interface JsonRpcMessage permits JsonRpcRequest, JsonRpcNotification, JsonRpcResponse {
    String VERSION = "2.0";

    default String jsonrpc() {
        return VERSION;
    }
}
