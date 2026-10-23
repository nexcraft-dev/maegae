# Maegae

**A lightweight, high-performance MCP server framework for Java.**

Maegae is an MCP server framework in early development, built around Java 25, Netty, and Virtual Threads.

The planned v1 API will use Java annotations for tool definitions, with generated tool metadata, JSON Schema, and invocation adapters.

No Spring. No dependency injection container. No unnecessary complexity.

> **Project Status:** Early Development

## Implemented Foundation

- Immutable internal JSON values, strict parsing, and serialization using Jackson 3 Core.
- JSON-RPC 2.0 message models, parsing, validation, serialization, and standard error response factories.
- MCP initialization and capability models targeting [MCP 2025-11-25](https://modelcontextprotocol.io/specification/2025-11-25/basic/lifecycle).
- Architecture checks for Core independence, internal type encapsulation, Processor boundaries, and Netty transport boundaries.

The JSON-RPC and MCP models are package-private Core components. Initialization handling, method dispatch, version negotiation, and JSON-RPC-to-MCP mapping remain planned.

JSON-RPC supports individual requests, notifications, and success/error responses. Batch messages are not supported. Initialization models preserve optional title, description, instructions, and `_meta`. Implementation icons and `websiteUrl` are not modeled.

The server capability model provides a Tools-only declaration with `listChanged=false`. This declaration does not enable tool execution or runtime capability advertisement. Resources and Prompts are absent from this default.

The JSON codec enforces these parsing limits:

| Limit | Default |
|-------|---------|
| UTF-8 input size | 1 MiB (1,048,576 bytes) |
| Nesting depth | 64 objects or arrays |
| Numeric token length | 1,000 characters |

Duplicate object properties and malformed JSON are rejected. Input and nesting limits can be set on the internal codec. No public protocol configuration API is available.

## Planned v1 Features

- **Java 25** — Built for modern Java.
- **Virtual Threads** — Lightweight concurrent tool execution.
- **Netty** — High-performance HTTP transport.
- **Annotation-Based Tools** — Define MCP tools using Java methods.
- **Compile-Time Processing** — Generate tool metadata, JSON Schema, and invocation adapters at compile time.
- **Reflection-Free Tool Invocation** — Generated adapters invoke tool methods directly.
- **Stateless HTTP** — Streamable HTTP without server-side session management.
- **Minimal Dependencies** — No application framework required.
- **Configurable Execution** — Tool timeouts and optional concurrency limits.

## Planned Quick Start

> The following API represents the planned Maegae v1 developer experience. It is not yet available as a published release.

### 1. Define Your Tools

```java
public class CalculatorTools {

    @Tool(description = "Add two numbers")
    public int add(
            @Arg(name = "a") int a,
            @Arg(name = "b") int b) {

        return a + b;
    }

    @Tool(description = "Multiply two numbers")
    public int multiply(
            @Arg(name = "a") int a,
            @Arg(name = "b") int b) {

        return a * b;
    }
}
```

Maegae will use annotation processing to generate tool definitions, input schemas, and invocation adapters during compilation.

### 2. Start the Server

```java
public class Application {

    public static void main(String[] args) {
        Maegae.server()
            .tools(new CalculatorTools())
            .listen();
    }
}
```

The planned server defaults are:

| Setting | Default |
|---------|---------|
| Host | `127.0.0.1` |
| Port | `8765` |
| Endpoint | `/mcp` |
| Transport | Streamable HTTP |
| Session Management | Stateless |
| Tool Timeout | 30 seconds |
| Tool Concurrency | Unlimited |
| Execution Model | Virtual Threads |

The planned MCP endpoint:

`http://127.0.0.1:8765/mcp`

### 3. Customize the Server

```java
Maegae.server()
    .tools(new CalculatorTools())
    .toolTimeout(Duration.ofSeconds(60))
    .maxConcurrentToolCalls(100)
    .listen(9000);
```

Planned configuration options:

- `toolTimeout()` — Maximum execution time for a tool call.
- `maxConcurrentToolCalls()` — Optional limit on concurrent tool executions.
- `listen(port)` — Start the HTTP server on a custom port.

The planned default has no tool concurrency limit.

## MCP Feature Support

The following matrix distinguishes implemented Core foundations from planned runtime capabilities.

**Status Legend**

- ✅ Supported — Implemented and tested
- 🚧 In Progress — Currently being implemented
- 📋 Planned — Planned for a future release
- ⬜ Not Implemented — Not currently implemented or scheduled

### Protocol Foundation

| Feature | Status |
|---------|--------|
| Internal JSON values and codec | ✅ Supported |
| JSON-RPC 2.0 message parsing and serialization | ✅ Supported |
| Standard JSON-RPC error response factories | ✅ Supported |
| MCP initialization and capability models (2025-11-25) | ✅ Supported |

These entries describe internal model and codec support. Runtime support is listed separately below.

### Lifecycle

| Feature | Status |
|---------|--------|
| Initialization handling | 📋 Planned |
| Initialized Notification | 📋 Planned |
| Ping | 📋 Planned |
| Protocol Version Negotiation | 📋 Planned |

### Tools

| Feature | Status |
|---------|--------|
| List Tools | 📋 Planned |
| Call Tool | 📋 Planned |
| Tool Input Validation | 📋 Planned |
| Automatic JSON Schema Generation | 📋 Planned |
| Annotation-Based Registration | 📋 Planned |
| Tool List Changed Notification | ⬜ Not Implemented |

### Resources

| Feature | Status |
|---------|--------|
| List Resources | ⬜ Not Implemented |
| Read Resource | ⬜ Not Implemented |
| Resource Templates | ⬜ Not Implemented |
| Resource Subscriptions | ⬜ Not Implemented |
| Resource Update Notifications | ⬜ Not Implemented |

### Prompts

| Feature | Status |
|---------|--------|
| List Prompts | ⬜ Not Implemented |
| Get Prompt | ⬜ Not Implemented |
| Prompt List Changed Notification | ⬜ Not Implemented |

### Additional Capabilities

| Feature | Status |
|---------|--------|
| Argument Completion | ⬜ Not Implemented |
| Progress Notifications | ⬜ Not Implemented |
| Request Cancellation | ⬜ Not Implemented |
| Logging | ⬜ Not Implemented |
| Sampling | ⬜ Not Implemented |
| Elicitation | ⬜ Not Implemented |
| Roots | ⬜ Not Implemented |

### Transport

| Feature | Status |
|---------|--------|
| Streamable HTTP | 📋 Planned |
| Stateless Execution | 📋 Planned |
| Origin Validation | 📋 Planned |
| STDIO | ⬜ Not Implemented |
| Stateful Sessions | ⬜ Not Implemented |
| SSE Streaming | ⬜ Not Implemented |

### Security

| Feature | Status |
|---------|--------|
| Localhost Binding by Default | 📋 Planned |
| HTTP Origin Validation | 📋 Planned |
| OAuth 2.0 Authorization | 📋 Planned (Post-v1) |

## Architecture

The planned v1 architecture separates MCP protocol processing from its underlying HTTP transport implementation.

```text
                    Java Application
                           |
                           v
                    Maegae.server()
                           |
                           v
                  Tool Registration
                           |
                           v
                 Generated Tool Adapters
                           |
                           v
                      MCP Core
                           |
                           v
                     Transport SPI
                           |
                           v
                    Netty Transport
                           |
                           v
                    HTTP /mcp
```

### Module Structure

```text
maegae/
├── maegae-core/
├── maegae-processor/
├── maegae-transport-netty/
└── maegae-example/
```

| Module | Responsibility |
|--------|----------------|
| `maegae-core` | MCP protocol, tool registry, execution, and transport abstractions |
| `maegae-processor` | Compile-time annotation processing and code generation |
| `maegae-transport-netty` | Netty-based Streamable HTTP transport |
| `maegae-example` | Example applications |

The MCP core is independent of Netty.

This allows the underlying HTTP implementation to evolve without affecting the core MCP API.

## Technology Stack

| Component | Technology |
|-----------|------------|
| Language | Java 25 |
| HTTP Transport | Netty 4.2 |
| JSON Processing | Jackson 3 Core |
| Logging | SLF4J 2.x |
| Concurrency | Java Virtual Threads |
| Annotation Processing | Java Annotation Processing API |
| Testing | JUnit Jupiter 6 |
| Build | Gradle |
| License | Apache License 2.0 |

Maegae does not require Spring, Micronaut, or another application framework.

## Development

Use JDK 25 and the Gradle wrapper to verify the protocol components and all module boundaries:

```bash
./gradlew :maegae-core:test
./gradlew clean build
```

## Design Principles

### 1. Lightweight by Default

Keep dependencies minimal and avoid unnecessary abstractions.

### 2. Compile-Time Over Runtime

Generate tool metadata and invocation adapters during compilation instead of relying on runtime reflection.

### 3. Virtual-Thread Friendly

Execute user-defined tools on Virtual Threads without blocking Netty EventLoop threads.

Virtual Threads are not pooled.

### 4. Transport Independence

Keep MCP protocol logic independent of Netty and other HTTP server implementations.

### 5. Explicit Configuration

Provide sensible defaults while allowing developers to configure execution timeouts, concurrency limits, and server ports.

## Roadmap

### v1 — Tool-Focused MCP Server

- [x] Project foundation and architecture checks
- [x] JSON Foundation
- [x] JSON-RPC message parsing, serialization, and standard errors
- [x] MCP initialization and capability models (2025-11-25)
- [ ] MCP method dispatch and lifecycle integration
- [ ] Tool annotations
- [ ] Annotation processor
- [ ] JSON Schema generation
- [ ] Generated tool invocation adapters
- [ ] Tool registry
- [ ] Tool execution with Virtual Threads
- [ ] Tool timeout handling
- [ ] Optional concurrency limiting
- [ ] Stateless Streamable HTTP transport
- [ ] Netty integration
- [ ] Origin validation
- [ ] MCP runtime compatibility tests
- [ ] Example application
- [ ] Maven Central publishing

### Future Considerations

- OAuth 2.0 authorization
- Resources
- Prompts
- Progress notifications
- Request cancellation
- Stateful transport
- STDIO transport
- Additional MCP capabilities

Future features will be evaluated based on demand and their impact on framework simplicity.

## Security

Maegae is designed for local development by default.

The initial version will:

- Bind to `127.0.0.1` by default.
- Validate HTTP Origin headers.
- Avoid exposing unauthenticated services publicly by default.

OAuth authentication is not part of the initial release.

Developers should use appropriate authentication and network access controls when exposing MCP servers beyond trusted local environments.

## License

Maegae is licensed under the [Apache License 2.0](LICENSE).
