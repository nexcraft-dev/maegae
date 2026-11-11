# Maegae — Project Handover

Last Updated: 2026-11-11
Project: Maegae
Status: M04-T05 merged into main. M04-T06 is next.
Next Task: M04-T06 — Integration & Hardening

---

## 0. How to read this document

Claims have different levels of verification. Do not treat all of them as equal.

- **User-reported**: the user told the assistant. Not inspected.
- **Agent-reported**: a coding agent reported it. The assistant did not inspect the repository. Inspect the actual repository before relying on exact names, line numbers, or counts.
- **Researched**: found on the web on 2026-11-11, mostly from secondary sources (see section 4).

The assistant that wrote this document had no repository access. Section 15 lists what to verify first.

---

## 1. Project Overview

Maegae is a lightweight, annotation-driven Java 25 MCP (Model Context Protocol) Server Framework. It lets Java developers expose ordinary Java methods as MCP Tools with minimal configuration, without Spring Boot, Micronaut, or other heavy frameworks.

Priorities:
- Lightweight runtime, low memory overhead.
- Compile-time code generation instead of runtime reflection.
- Simple Java APIs, strong separation of concerns, clean architecture.
- Stateless MCP communication.
- Standards-compliant JSON-RPC and MCP behavior.
- Deterministic, meaningful automated tests.
- Minimal external dependencies.

Target developer experience (a target, not an implemented runtime):

```java
public class CalculatorTools {
    @Tool(description = "Adds two numbers")
    public int add(
            @Arg(description = "First number") int a,
            @Arg(description = "Second number") int b
    ) {
        return a + b;
    }
}

public class Application {
    public static void main(String[] args) {
        Maegae.server()
                .tools(new CalculatorTools())
                .listen();
    }
}
```

The runtime (registry, dispatcher, transport, public server API) is NOT implemented. Do not claim it is.

---

## 2. Technology Stack and Decisions

| Area | Technology |
|---|---|
| Language | Java 25 |
| Build | Gradle 9.8.1 |
| MCP Transport | Streamable HTTP (stateless) |
| HTTP Engine | Netty 4.2 |
| Concurrency | Java Virtual Threads |
| JSON | Jackson 3 Core Streaming (no Databind) |
| JSON Representation | Internal immutable JSON model |
| Annotation Processing | Java Annotation Processing API |
| Testing | JUnit Jupiter 6, ArchUnit |
| License | Apache License 2.0 |
| Base Package | dev.nexcraft.maegae |

Decisions:
1. Use Java 25 features where appropriate.
2. No Spring Boot or other heavy framework.
3. Jackson stays inside the JSON implementation. No Jackson Databind.
4. No runtime reflection for Tool discovery and invocation.
5. The MCP core is independent of Netty.
6. Prefer compile-time validation and generated adapters.
7. Keep the public API small. Avoid unnecessary abstractions and dependencies.

---

## 3. Module Architecture

```
maegae/
├── maegae-core/
├── maegae-processor/
├── maegae-transport-netty/
├── maegae-example/
└── build.gradle / settings.gradle
```

- **maegae-core**: JSON foundation, JSON-RPC models and parsing, MCP protocol models, Tool contracts and runtime metadata, transport-independent logic. Must not depend on Netty.
- **maegae-processor**: @Tool/@Arg processing, validation, metadata and JSON Schema generation, invocation adapter generation. The processor class name is `McpToolProcessor`. Do not rename it.
- **maegae-transport-netty**: Netty Streamable HTTP transport. Netty details stay isolated from core.
- **maegae-example**: not a priority until the runtime works.

---

## 4. MCP Protocol Version and Transport

### Decision

**v1 targets MCP 2025-11-25.** Keep annotation-processing work (M04) version-neutral. Plan 2026-07-28 support as a separate later milestone. Keep the list of negotiable protocol versions configurable.

Reasons: M03 built `initialize` models. The 2026-07-28 changes (below) are dispatcher and transport work and do not affect M04. The user preferred 2025-11-25 unless the newer spec had a compelling advantage.

### Researched facts (2026-11-11; secondary sources, the official 2026-07-28 changelog was not fetched directly)

- The 2026-07-28 spec was published on 2026-07-28 (release candidate was locked 2026-05-21). It is no longer an RC.
- Headline change: the protocol is stateless. No `initialize` handshake, no `Mcp-Session-Id`. Every request carries `_meta` with protocol version and client capabilities. New `server/discover` method.
- Streamable HTTP requires `Mcp-Method` on every request and `Mcp-Name` on `tools/call`, `resources/read`, `prompts/get`. `MCP-Protocol-Version` header must match `_meta`.
- HTTP GET endpoint and SSE stream resumability were removed. Roots, Sampling, Logging, HTTP+SSE transport, and Dynamic Client Registration are deprecated. Tasks became an extension.
- In 2026-07-28, `outputSchema` and `structuredContent` may have any JSON root. In 2025-11-25 and earlier they require an object root.
- Tool error semantics are the same in both versions (see section 9). SEP-1303 (2025-11-25) moved input validation errors from protocol errors to Tool execution errors (`isError: true`).
- Many SDKs and servers serve 2026-07-28 natively and fall back to 2025-era behavior. Fallback behavior of each real client was NOT verified. Verify interoperability in the runtime milestone.

### Not verified

- The protocol version string that M03 actually selected. The user did not report the result of `grep -rn "2025-\|2026-" maegae-core/src`. Inspect M03 before the runtime milestone.
- Whether the spec allows an empty `content` array in `CallToolResult`. T05 maps void/null returns to an empty output. Verify before the dispatcher is built.

### Transport design (v1)

| Setting | Default |
|---|---|
| Host | 127.0.0.1 |
| Port | 8765 |
| Endpoint | /mcp |
| Tool timeout | 30 seconds |
| Tool concurrency | Unlimited by default, optionally configurable |
| Execution model | Virtual threads |

Principles: no per-client session management in v1, no stateful transport, no OAuth in v1, validate HTTP Origin, no unnecessary persistent connections, no message broker, keep HTTP separate from MCP logic.

---

## 5. JSON Foundation and JSON-RPC (M02, M03)

```
dev.nexcraft.maegae.internal.json
├── JsonValue, JsonObject, JsonArray, JsonString, JsonNumber, JsonBoolean, JsonNull
├── JsonCodec
└── JacksonJsonCodec
```

Policies: immutable values; BigDecimal for numbers; strict parsing; duplicate property rejection; max input 1 MiB; max nesting depth 64; numeric precision preserved. Agent-reported: the codec limits a number token to 1,000 characters (`MAX_NUMBER_LENGTH`) and rejects exponents outside the `int` range. Do not add a second JSON representation. Do not expose internal JSON types through public API/SPI.

```
dev.nexcraft.maegae.protocol
├── JsonRpcMessage, JsonRpcId, JsonRpcRequest, JsonRpcNotification
├── JsonRpcResponse, JsonRpcError
└── JsonRpcMessageParser, JsonRpcMessageSerializer
```

JSON-RPC ID policy: string IDs supported; numeric IDs use BigDecimal and keep precision; request IDs cannot be null; notifications have no ID; successful responses cannot have null IDs; error responses may have a null ID when the request ID is unknown.

Standard errors: -32700 Parse error, -32600 Invalid Request, -32601 Method not found, -32602 Invalid params, -32603 Internal error. Error responses must not expose stack traces or internal details.

M03 also added MCP initialize models (request params, result, protocol version, implementation info, capabilities). Initialize dispatching and version negotiation are runtime work.

---

## 6. Annotations and Processor (M04-T01 to T03)

```java
@Target(ElementType.METHOD) @Retention(RetentionPolicy.CLASS)
public @interface Tool { String name() default ""; String description() default ""; }

@Target(ElementType.PARAMETER) @Retention(RetentionPolicy.CLASS)
public @interface Arg { String name() default ""; String description() default ""; boolean required() default true; }
```

Empty name means the Java method or parameter name. Unannotated parameters are required.

Processor behavior: discovers @Tool methods, resolves effective names, validates declarations (agent-reported: non-public methods are rejected with "must be public"), detects duplicate Tool names within a provider and duplicate argument names, reports actionable diagnostics, supports normal and nested provider classes, uses no runtime reflection.

**Known limitation (approved in T03):** discovery is limited to declarations exposed by RoundEnvironment. Local classes are outside the supported scope. Their @Tool annotations may not be discovered or rejected. Do not claim all unsupported declarations produce compile errors. Do not add compiler-tree scanning for local classes. Keep this limitation documented.

---

## 7. Tool Metadata and Schema (M04-T04)

Agent-reported state after T05:

- One generated file per provider: `MaegaeToolMetadata_<suffix>`. `public final class`, private constructor, same package as the provider. One public method: `public static List<ToolDefinition> tools()` returning an immutable list sorted by Tool name.
- `ToolDefinition(String name, Optional<String> description, String inputSchemaJson)` is a public record in `maegae-core` (`protocol/mcp/`). `inputSchemaJson` is a JSON object stored as a string. A future serializer must insert it as a JSON object, not as a JSON string.
- Long schema strings are split into 8192-character chunks (`STRING_CHUNK_LENGTH`).
- If any declaration fails, no file is generated.
- Processor classes: `ToolDeclaration` and `ArgumentDeclaration` (package-private records), `ToolSchemaGenerator`, `ToolMetadataGenerator` (now a renderer), `ToolGenerator` (writes both files together), `ToolInvokerGenerator`, `ToolType`, `JavaSourceText`, `GeneratedNames`.

Type mapping (schema and conversion share `ToolType`):

| Java type | JSON Schema |
|---|---|
| String | string |
| boolean / Boolean | boolean |
| byte, short, int, long and boxed; BigInteger | integer |
| float, double and boxed; BigDecimal | number |

Not supported: POJOs, records, collections, arrays, maps, enums, Optional, generic parameterized types.

### Generated class naming (changed in T05)

T04 originally used the hex of the UTF-8 bytes of the binary name as the suffix. Hex doubles the length, and a long package plus a nested class gave a 299-byte file name that failed with `File name too long` (found by a T05 preflight test). Commit `c629cb4` replaced it with a SHA-256 hex digest (64 characters, fixed length, so the file name is 88 characters). A hash collision between two providers is a compile error (`GeneratedNames`). This was verified only on macOS.

---

## 8. Invocation Adapters (M04-T05, merged)

### Public contract (package `dev.nexcraft.maegae.tool`, in maegae-core)

```java
public interface ToolArguments {
    boolean has(String name);      // key exists, including JSON null
    boolean isNull(String name);   // key exists and the value is JSON null
    String getString(String name);
    boolean getBoolean(String name);
    byte getByte(String name);
    short getShort(String name);
    int getInt(String name);
    long getLong(String name);
    float getFloat(String name);
    double getDouble(String name);
    BigInteger getBigInteger(String name);
    BigDecimal getBigDecimal(String name);
    // getters throw ToolArgumentException when the key is missing or null,
    // the type is wrong, or the value is out of range
}

public final class ToolArgumentException extends RuntimeException { /* argumentName() */ }
public final class ToolOutput { static ToolOutput text(String); static ToolOutput empty(); Optional<String> text(); }
public interface ToolInvoker { ToolOutput invoke(ToolArguments args) throws Exception; }
```

- `ToolArgumentException` messages contain only the argument name and the expected type. They never contain the supplied value.
- The implementation is `internal/tool/JsonToolArguments` with the factory `JsonToolArguments.of(JsonObject)`. It delegates to the internal JSON model. No internal JSON type appears in public API.
- Why this design: generated code must not know JSON types. Numeric policy lives in one place. A public JSON value contract was rejected because it makes the whole JSON model public API and duplicates conversion logic in every adapter.

### Generated adapter

- One file per provider: `MaegaeToolInvokers_<same suffix rule>`. `public final class`, private constructor, same package as the provider.
- One public method: `static Map<String, ToolInvoker> invokers(Provider provider)`. Keys are effective Tool names (same as in `ToolDefinition`). Immutable, in Tool name order. A null provider throws NullPointerException. A generic provider class takes `Provider<?>`.
- Both files (metadata and invokers) are generated together. If any declaration fails, neither is generated.
- Member names come from Java method names, never Tool names. Collisions get a numeric suffix. A collision with a user class is a compile error. Provider elements are passed as originating elements to Filer. Output is deterministic.
- The adapter does not catch exceptions from the Tool method. They propagate unchanged.

### Argument rules

- Required parameter: call the matching getter. Missing or JSON null gives `ToolArgumentException`.
- `required = false` on a nullable type (boxed types, String, BigInteger, BigDecimal): pass null when the argument is missing or JSON null.
- `required = false` on a primitive parameter: compile error.
- Unknown extra arguments are ignored (consistent with the generated schemas, which do not set `additionalProperties: false`).

### Numeric conversion rules (all in JsonToolArguments)

- JSON numbers only. Strings and booleans are rejected. No coercion.
- byte/short/int/long/BigInteger: accept integral values, including `1.0` and `1e2`. Reject fractional and out-of-range values.
- Prechecks on scale and precision run before any exact conversion. `MAX_INTEGER_DIGITS = 1000` (agent-reported; equals the codec token limit) limits both integer digits and significant digits.
- float/double: precision loss is allowed. Infinity results are rejected. Finite underflow to zero is allowed (keeps the sign) and is documented.
- BigDecimal: passed exactly, no rounding.

Mutation results (agent-reported, JDK 25):

| Removed check | Result |
|---|---|
| `integerDigits > MAX_INTEGER_DIGITS` | Killed by tests. Without it `1e100000000` takes about 24 s and `1e500000000` runs out of memory after about 255 s. |
| `precision > MAX_INTEGER_DIGITS` | Killed by a test that builds a `BigDecimal` directly (the codec cannot reach this branch). |
| zero shortcut | Killed. |
| `integerDigits <= 0` | Survives. Result and time are the same on JDK 25. |
| floating-point exponent precheck | Survives. Result and time are the same on JDK 25. |

The two surviving checks stay as guards that do not depend on JDK internals. T06 adds a code comment saying so.

### Return values

Supported return types: void; String; boolean, byte, short, int, long, float, double and boxed types; BigInteger; BigDecimal. Any other return type is a compile error that names the Tool.

- void and null results: `ToolOutput.empty()`.
- String: as is. Other types: `String.valueOf(result)`. BigDecimal uses `toString()` (for example `1E+2`). `NaN` becomes the text `NaN`.
- No `structuredContent` or `outputSchema` in v1 so far.

---

## 9. Error Boundaries

| Situation | Result |
|---|---|
| Missing required argument, wrong type, out of range, fractional integer | Tool execution error (`isError: true`) |
| Exception thrown by the Tool method | Tool execution error. Generic message only. No stack trace and no exception details. |
| Unknown Tool name | JSON-RPC -32602 |
| Malformed `tools/call` structure (missing `name`, `arguments` not an object) | JSON-RPC -32602 |
| Unknown method | JSON-RPC -32601 |
| Internal framework error | JSON-RPC -32603 |

- The future dispatcher, not the generated adapter, converts failures into `CallToolResult` or JSON-RPC errors.
- Keep `ToolArgumentException` distinguishable from other exceptions.
- Never expose supplied argument values, stack traces, or internal exception details.

---

## 10. Milestone Progress

| Milestone | Status |
|---|---|
| A01–A03 (ArchUnit, CI) | Complete |
| M01 Project Foundation | Complete |
| M02 JSON Foundation | Complete |
| M03 MCP Protocol | Complete |
| M04-T01 Annotation Contract | Complete |
| M04-T02 Processor Foundation | Complete |
| M04-T03 Discovery & Validation | Complete |
| M04-T04 Metadata & JSON Schema | Complete, merged |
| M04-T05 Invocation Adapter Generation | Complete, merged (user-reported). PR #20, merge commit a25372b |
| M04-T06 Integration & Hardening | Next |

T05 commits on the feature branch (agent-reported): `c629cb4` (hash-based class names), `4e070af` (adapter generation), `a17a557` (test hardening). The merge method may have changed these hashes.

Test counts after `a17a557` (agent-reported): core 382, processor 80, netty 35, no failures.

M04 was planned with seven tasks. The metadata and schema tasks were merged into one, so M04 has six.

---

## 11. Next Task — M04-T06 Integration & Hardening

Start from main after the T05 merge. Branch: `feat/m04-t06-integration-hardening`.

Preflight:
1. Check how the processor is registered for normal javac discovery (`META-INF/services/javax.annotation.processing.Processor` or equivalent). All tests so far used a test harness, so real-build discovery is unverified.
2. Report what a user must declare in Gradle (annotationProcessor, compileOnly, implementation) because generated code uses core types. Do not change dependencies before reporting.

Work items:
- **A. Consistency (core of T06).** Compile providers covering all supported types. Assert that `ToolDefinition` names and invoker map keys are the same, in the same order. For every supported Java type, assert the invoker accepts a valid value for its schema type and rejects a wrong JSON type. Assert the schema `required` list matches invoker behavior. Assert descriptions and name overrides match.
- **B. Multiple providers.** Top-level public, package-private, public static nested, default package, same simple name in different packages. Assert generated files and that each adapter runs the right provider. Compile twice and compare all files. Same Tool name in different providers: document that cross-provider duplicate detection is future registry work. Do not implement it.
- **C. Warnings.** Compile all generated sources with `-Xlint:all -Werror`. Fix the generator if warnings appear (for example `Provider<?>`). Run a generic provider.
- **D. Failure behavior.** Valid provider plus invalid provider in one compilation. Document and test the actual file behavior. If it is unsafe, stop and report. Spot-check three diagnostics for line number and for naming the Tool, argument, and type. The unsupported return type diagnostic must name the offending type (T05 review found its test did not assert this).
- **E. Rounds.** Another processor generates sources in round one. Assert no double generation. Assert Filer originating elements are set on both files.
- **F. Boxed null returns.** One parameterized test for null returns of Boolean, Byte, Short, Long, Float, Double, BigInteger giving `ToolOutput.empty()`.
- **G. Comment.** Add the hardening comment at the two surviving prechecks. No behavior change.
- **H. README.** Update the support matrix and the limitations section: local classes not discovered, no cross-provider duplicate detection, no `structuredContent`/`outputSchema`, no POJO/record/collection/array/enum/Optional support, MCP 2025-11-25 target, runtime not implemented.

Verification: run `./gradlew clean build` twice (checks flakiness, including the 5 s preemptive timeout test in `JsonToolArgumentsTest`).

Out of scope: registry, dispatcher, `CallToolResult` conversion, HTTP transport, sessions, authentication, POJO conversion, `structuredContent`, version negotiation, the `maegae-example` module. Do not implement the runtime just to test annotation processing.

---

## 12. Open Items for Later Milestones

- Tool registry: register adapters, associate with provider instances, detect duplicate Tool names across providers.
- How the runtime finds generated classes without reflection (for example `ServiceLoader`). This needs public classes and public constructors, which is a registration-phase decision.
- Dispatcher: `initialize`, version negotiation, `tools/list`, `tools/call`, JSON-RPC error semantics, no response to valid notifications, mapping of `ToolArgumentException` and other exceptions per section 9, conversion of `ToolOutput` to `CallToolResult`. Verify that the spec allows an empty `content` array.
- Tool execution: virtual threads, configurable timeouts (a timeout may not interrupt blocking code), optional concurrency limits.
- Netty Streamable HTTP: stateless, `/mcp`, Origin validation, request limits.
- 2026-07-28 support: `server/discover`, `_meta`, `Mcp-Method`/`Mcp-Name` headers, any-root `structuredContent`.
- `structuredContent`/`outputSchema` generation (object root is required in 2025-11-25, so scalar returns need wrapping).
- Public server API (`Maegae.server()...listen()`), a minimal example, real MCP client interoperability.
- README roadmap checkboxes were not updated in T05.
- CI: check whether the OS matrix includes a non-macOS system. The 255-byte file-name fix was only verified on macOS.
- stateless mode has no server-initiated requests. Note this in the README limitations when the runtime exists.

---

## 13. Development Workflow

1. Discuss scope before implementation when architecture decisions are unresolved. Resolve only decisions essential to the task.
2. One clearly bounded task at a time. Lower-level dependencies first.
3. Do not change unrelated components. Preserve existing names unless a rename is approved.
4. No speculative abstractions.
5. Verify the full build before marking a task complete. Report actual status, not assumed status.
6. Update the README support matrix when capabilities change.

Coding agent prompts (English) include: objective, scope, architecture constraints, fixed decisions, implementation requirements, testing expectations, acceptance criteria, verification commands, and a completion report. Lessons from M04-T05:
- Start the prompt with "Implement this task now. Do not summarize any previous task." An agent once answered with a summary of the previous task instead of implementing.
- Ask the agent for evidence (file, test method, assertion) when reviewing its report, and do not accept counts alone.
- Review reports for claims that were not verified, for tests that were silently dropped, and for tests that do not reach the code they claim to test (for example, the codec rejecting an input before the code under test sees it).
- Use mutation checks on safety-critical code. Remove a check, run the tests, and require a failure. If a mutation survives, either add a test or document why the check is redundant.
- The agent sandbox can block Gradle writes to `~/.gradle`. The agent may need to run Gradle outside the sandbox.

Testing policy — no AI slop. Do not add tests only for coverage. Avoid trivial assertions, redundant cases, excessive mocking, brittle implementation-detail tests, and tests that only check that a class exists. Prefer real compiler integration tests, generated-code execution tests, deterministic behavioral assertions, boundary and failure-path tests, and architecture regression tests.

Git: the user works with feature branches, PRs, and merges. Do not force-push, reset, or discard commits. Do not push to main directly.

---

## 14. Standard Verification Commands

```
./gradlew :maegae-core:test
./gradlew :maegae-processor:test
./gradlew clean build
```

All tests and ArchUnit checks must pass. Do not weaken ArchUnit rules.

---

## 15. Instructions for the Next AI Assistant

You are continuing development of Maegae. M01–M03 and M04-T01 to T05 are done. M04-T06 is next.

Before writing the T06 prompt:
1. Confirm with the user that the T05 PR is merged and ask for the merge commit hash if it is not in section 10.
2. Ask for the result of `grep -rn "2025-\|2026-" maegae-core/src` to learn the protocol version M03 selected. It was never reported.
3. Ask the user for the repository layout of the T04/T05 generated files if exact names matter.
4. Do not assume details marked "agent-reported" are exact.

When the user asks for the T06 prompt, write it in English from section 11. It must start with "Implement this task now. Do not summarize any previous task." and include the Preflight, scope, fixed decisions, work items A–H, test rules, verification (build twice), acceptance criteria, and the completion report.

After T06, update this document and plan the runtime milestones from section 12. Confirm milestone numbering and task decomposition with the user before implementing. Do not claim runtime capabilities are complete until verified.

Current checkpoint: M04-T05 merged. M04-T06 next.
