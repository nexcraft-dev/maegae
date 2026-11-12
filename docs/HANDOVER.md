# Maegae — Project Handover

Last Updated: 2026-11-13
Project: Maegae
Status: Milestones M01–M05 are complete and merged into main. The server runtime (dispatcher, execution, transport, public API) is not implemented.
Next Task: M06 — MCP dispatcher. Not planned in detail yet (section 11).

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
| CI | GitHub Actions, `ubuntu-latest` only (agent-reported) |
| Native build | GraalVM native-image is a REQUIREMENT (user decision). Local runs use GraalVM CE for JDK 25 installed with sdkman. |
| License | Apache License 2.0 |
| Base Package | dev.nexcraft.maegae |

Decisions:
1. Use Java 25 features where appropriate.
2. No Spring Boot or other heavy framework.
3. Jackson stays inside the JSON implementation. No Jackson Databind.
4. Maegae never uses reflection to find or call Tool methods. Binding discovery uses java.util.ServiceLoader (the JDK may use reflection internally; GraalVM native-image supports it). See section 9a.
5. The MCP core is independent of Netty.
6. Prefer compile-time validation and generated adapters.
7. Keep the public API small. Avoid unnecessary abstractions and dependencies.
8. The framework must work in a GraalVM native image. Do not claim general native-image support until it is verified for the full runtime (section 9a lists what was verified).

---

## 3. Module Architecture

```
maegae/
├── maegae-core/
├── maegae-processor/
├── maegae-transport-netty/
├── maegae-example/
├── maegae-native-smoke/
└── build.gradle / settings.gradle
```

- **maegae-core**: JSON foundation, JSON-RPC models and parsing, MCP protocol models, Tool contracts and runtime metadata, transport-independent logic. Must not depend on Netty. Agent-reported dependencies seen through Gradle: jackson-core 3.2.2 and slf4j-api 2.0.20. It also contains the package `dev.nexcraft.maegae.runtime` (Tool registry, later the dispatcher). See section 9a.
- **maegae-processor**: @Tool/@Arg processing, validation, metadata and JSON Schema generation, invocation adapter generation. The processor class name is `McpToolProcessor`. Do not rename it. It declares `implementation(project(":maegae-core"))`.
- **maegae-transport-netty**: Netty Streamable HTTP transport. Netty details stay isolated from core.
- **maegae-example**: not built yet. Not a priority until the runtime works.
- **maegae-native-smoke**: test-only, not published, not part of `build` or `check`. Task `:maegae-native-smoke:nativeSmokeTest`. See section 9a.

---

## 4. MCP Protocol Version and Transport

### Decision

**v1 targets MCP 2025-11-25.** Keep annotation-processing work version-neutral. Plan 2026-07-28 support as a separate later milestone. Keep the list of negotiable protocol versions configurable.

Reasons: M03 built `initialize` models. The 2026-07-28 changes (below) are dispatcher and transport work. The user preferred 2025-11-25 unless the newer spec had a compelling advantage.

### Researched facts (2026-11-11; secondary sources, the official 2026-07-28 changelog was not fetched directly)

- The 2026-07-28 spec was published on 2026-07-28 (release candidate was locked 2026-05-21). It is no longer an RC.
- Headline change: the protocol is stateless. No `initialize` handshake, no `Mcp-Session-Id`. Every request carries `_meta` with protocol version and client capabilities. New `server/discover` method.
- Streamable HTTP requires `Mcp-Method` on every request and `Mcp-Name` on `tools/call`, `resources/read`, `prompts/get`. `MCP-Protocol-Version` header must match `_meta`.
- HTTP GET endpoint and SSE stream resumability were removed. Roots, Sampling, Logging, HTTP+SSE transport, and Dynamic Client Registration are deprecated. Tasks became an extension.
- In 2026-07-28, `outputSchema` and `structuredContent` may have any JSON root. In 2025-11-25 and earlier they require an object root.
- Tool error semantics are the same in both versions (see section 9). SEP-1303 (2025-11-25) moved input validation errors from protocol errors to Tool execution errors (`isError: true`).
- Many SDKs and servers serve 2026-07-28 natively and fall back to 2025-era behavior. Fallback behavior of each real client was NOT verified. Verify interoperability in the runtime milestone.

### Resolved on 2026-11-13

- M03 selected 2025-11-25. The user ran `grep -rn "2025-\|2026-" maegae-core/src`. The Javadoc and tests of InitializeParams, InitializeResult, ClientCapabilities and ImplementationInfo name 2025-11-25. `ProtocolVersion` is a value type that validates the date format. It accepts valid dates such as 2024-11-05 and 2099-12-31 and rejects malformed or impossible dates. No supported-version list and no latest-version constant exist in main code. Version negotiation and the list of supported versions are dispatcher work.
- Empty `content` array in `CallToolResult`: the 2025-11-25 schema (a third-party mirror, not the official file) requires `content` and sets no minimum length. An empty array is valid. Void and null returns map to an empty `ToolOutput`. The dispatcher should send `content: []`.

### Still not verified

- Whether the M03 models match the 2025-11-25 schema field by field. As far as the assistant knows, 2025-11-25 added `icons` and `websiteUrl` to `Implementation`. `ImplementationInfo` documents only `title` and `description`. Unverified.
- `ToolDefinition` has no `title`, `annotations`, or `outputSchema`. This is fine for v1. Record it.

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

## 6. Annotations and Processor (M04-T01 to T03, T06)

```java
@Target(ElementType.METHOD) @Retention(RetentionPolicy.CLASS)
public @interface Tool { String name() default ""; String description() default ""; }

@Target(ElementType.PARAMETER) @Retention(RetentionPolicy.CLASS)
public @interface Arg { String name() default ""; String description() default ""; boolean required() default true; }
```

Empty name means the Java method or parameter name. Unannotated parameters are required.

Processor behavior: discovers @Tool methods, resolves effective names, validates declarations (non-public methods are rejected with "must be public"), detects duplicate Tool names within a provider and duplicate argument names, reports actionable diagnostics (line number, Tool, argument, type), supports normal and nested provider classes, uses no runtime reflection.

**Registration (agent-reported, T06):** `META-INF/services/javax.annotation.processing.Processor` lists `dev.nexcraft.maegae.processor.McpToolProcessor`. Tests compile with `--processor-path` and no explicit processor.

**Known limitation (approved in T03):** discovery is limited to declarations exposed by RoundEnvironment. Local classes are outside the supported scope. Their @Tool annotations may not be discovered or rejected. Do not claim all unsupported declarations produce compile errors. Do not add compiler-tree scanning for local classes. Keep this limitation documented.

### Annotation claiming (decision in T06)

`McpToolProcessor` supports `Tool` and `Arg` and returns `true` from `process`. Commit `cf73f0a` changed this from T02 (Tool only, returns false; the T02 reason was that no processing existed yet).

- Why: with `false`, every build with `-Xlint:all -Werror` failed with "No processor claimed any of these annotations" (reproduced by the agent). A standard processor has no other way to avoid that warning.
- Consequence: a processor that runs after Maegae does not see @Tool or @Arg on user classes. A processor that runs before Maegae does. A processor supporting `*` is called only in round 2 (agent-measured with a scratch observer processor).
- The user approved keeping the claim. The reason is documented in the processor Javadoc and in the README limitations (follow-up commit, see section 10).
- Note: the agent made this production-contract change without asking first, because the T06 prompt allowed `-Xlint` fixes. Future prompts should say "stop and report before changing a production contract".

---

## 7. Tool Metadata and Schema (M04-T04)

Agent-reported state:

- One generated file per provider: `MaegaeToolMetadata_<suffix>`. `public final class`, private constructor, same package as the provider (default package supported). One public method: `public static List<ToolDefinition> tools()` returning an immutable list sorted by Tool name.
- `ToolDefinition(String name, Optional<String> description, String inputSchemaJson)` is a public record in `maegae-core` (`protocol/mcp/`). `inputSchemaJson` is a JSON object stored as a string. A future serializer must insert it as a JSON object, not as a JSON string.
- Long schema strings are split into 8192-character chunks (`STRING_CHUNK_LENGTH`).
- If any declaration fails, no file is generated.
- Processor classes: `ToolDeclaration` and `ArgumentDeclaration` (package-private records), `ToolSchemaGenerator`, `ToolMetadataGenerator` (renderer), `ToolGenerator` (writes both files together), `ToolInvokerGenerator`, `ToolType`, `JavaSourceText`, `GeneratedNames`.
- Since M05-T01 a third class, `MaegaeToolBinding_<suffix>`, is generated for each provider. See section 9a.

Type mapping (schema and conversion share `ToolType`):

| Java type | JSON Schema |
|---|---|
| String | string |
| boolean / Boolean | boolean |
| byte, short, int, long and boxed; BigInteger | integer |
| float, double and boxed; BigDecimal | number |

Not supported: POJOs, records, collections, arrays, maps, enums, Optional, generic parameterized types.

### Generated class naming

T04 originally used the hex of the UTF-8 bytes of the binary name as the suffix. Hex doubles the length, and a long package plus a nested class gave a 299-byte file name that failed with `File name too long` (found by a T05 preflight test). Commit `c629cb4` replaced it with a SHA-256 hex digest (64 characters, fixed length, 88-character file name). A hash collision between two providers is a compile error (`GeneratedNames`). The fix was verified on macOS. CI runs on `ubuntu-latest`, so check the Linux result of the PRs.

---

## 8. Invocation Adapters (M04-T05)

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
- One public method: `static Map<String, ToolInvoker> invokers(Provider provider)`. Keys are effective Tool names (same as in `ToolDefinition`). Immutable, in Tool name order. A null provider throws NullPointerException. A generic provider class takes a wildcard type, for example `Box<?>` or `Pair<?, ?>`.
- Both files (metadata and invokers) are generated together. If any declaration fails, neither is generated for that provider.
- Member names come from Java method names, never Tool names. Collisions get a numeric suffix. A collision with a user class is a compile error. Provider elements are passed as originating elements to Filer (verified with a recording Filer). Output is deterministic.
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

The two surviving checks stay as guards that do not depend on JDK internals. Code comments say so (T06). A 5-second preemptive timeout test exists in `JsonToolArgumentsTest`. CI flakiness on slow runners was not checked.

### Return values

Supported return types: void; String; boolean, byte, short, int, long, float, double and boxed types; BigInteger; BigDecimal. Any other return type is a compile error that names the Tool and the offending type.

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

## 9a. Tool Discovery and Registry (M05)

### Discovery design (decided 2026-11-13)

Goal: `tools(new CalculatorTools())` keeps working, the framework works in a GraalVM native image, and Maegae never uses reflection to find or call Tool methods.

Options compared:
- **A**: compute the generated class name at run time and use `Class.forName` plus `Method.invoke`. Rejected. It needs native-image reflection metadata, because computed names are not visible to static analysis. The metadata format also changes between GraalVM versions.
- **B**: the processor generates a binding class per provider and one service file. The runtime uses `ServiceLoader`. **Chosen.**
- **C**: generated, readable binding classes that the developer calls directly: `tools(CalculatorToolsBinding.of(new CalculatorTools()))`. No reflection at all, but the target API changes. Kept as a fallback. It is additive, because the static generated classes already exist.

Why some reflection is unavoidable when the framework finds generated classes itself: core is compiled before user code exists, so it cannot name generated classes. Any lookup by name at run time is reflection. `ServiceLoader` uses reflection inside the JDK, and GraalVM native-image supports it.

### Generated artifacts per provider (agent-reported)

- `MaegaeToolMetadata_<sha256>` (T04), `MaegaeToolInvokers_<sha256>` (T05), `MaegaeToolBinding_<sha256>` (M05-T01). All are in the provider's package. All three are generated, or none.
- The binding implements `ToolProviderBinding` (package `dev.nexcraft.maegae.protocol.mcp`, in core): `Class<?> providerType()`, `List<ToolDefinition> tools()`, `Map<String, ToolInvoker> invokers(Object provider)`. It has a public no-argument constructor, because ServiceLoader requires one. `invokers` throws NullPointerException for null and IllegalArgumentException for a provider of the wrong type.
- The SPI is in `protocol.mcp`, not in `tool`. A dependency `tool -> protocol` creates the cycle `internal -> tool -> protocol -> internal` under the ArchUnit cycle rule (see below). The first plan put it in `tool`; the agent stopped at the failing ArchUnit test and did not weaken the rule.
- One service file, `META-INF/services/dev.nexcraft.maegae.protocol.mcp.ToolProviderBinding`, is written once in the last processing round. It is sorted, lists only providers whose three classes were generated, and is not written if an error was raised. A pre-existing resource with the same name is a compile error (no merging).
- The processor is declared **aggregating** in `META-INF/gradle/incremental.annotation.processors`. The cost is that Gradle reprocesses all annotated classes when one changes. A scratch Gradle consumer (composite build) showed correct service-file content after an edit and after deleting a provider. It was NOT confirmed from the logs that Gradle ran incrementally.

### Runtime registry (M05-T02, package `dev.nexcraft.maegae.runtime` in core)

```java
public final class ToolRegistry {
    public static ToolRegistry of(Collection<?> providers);   // ServiceLoader.load(ToolProviderBinding.class)
    public List<ToolDefinition> tools();                      // immutable, sorted by name (String.compareTo)
    public Optional<ToolInvoker> find(String name);
}
```

- A package-private `create(Iterable<ToolProviderBinding>, Collection<?>)` exists for tests with fake bindings.
- Provider match is exact: `providerType()` equals `provider.getClass()`. Subclasses and anonymous classes are not supported.
- The registry is immutable and thread-safe by construction. Provider instances may be called by many threads at once. Their thread safety is the developer's job.
- Errors (agent-reported): missing binding (IllegalStateException, names the class, says to add maegae-processor to annotationProcessor); two bindings for one type (IllegalStateException, names both bindings); Tool names differ from invoker names (IllegalStateException, lists the differences); duplicate Tool name across providers (IllegalArgumentException, `Duplicate Tool name 'x' in providers <A>, <B>.`, also for the same class or instance registered twice); null input (NullPointerException).
- Classes in `runtime` are public but internal to Maegae. No stability guarantee. Do not put a registry-like class in `internal`.
- Mutation checks (agent-reported): removing the names-equal-keys check, the duplicate check, the sort, or using `isInstance` instead of exact class equality each made tests fail. No mutation survived.

### ArchUnit facts (agent-reported, `CoreArchitectureRules` in maegae-core)

- `NO_PACKAGE_CYCLES` uses the first-level package under `dev.nexcraft.maegae` as the slice. `internal`, `internal.json` and `internal.tool` are one slice. Classes directly in the base package form `<bootstrap>`. `protocol` already depends on `internal`, so a class in `internal` that uses `protocol` creates a cycle. This is why `runtime` is a top-level package and not in `internal`.
- `CORE_INDEPENDENCE`: core must not use processor, transport.netty, example, io.netty.
- `TRANSPORT_INDEPENDENCE`: an allow-list for classes in `transport..`: JDK plus annotation, protocol, tool, transport, base package. **`runtime` is not on it.** The Netty transport therefore cannot use the registry or the dispatcher directly. Design M06 so that a small interface lives in `transport` and the dispatcher (in `runtime`) implements it. Do not edit the rule to get around this without an explicit decision.
- `ANNOTATION_INDEPENDENCE`, `INTERNAL_ENCAPSULATION`.
- Added in M05-T02: `RUNTIME_WITHOUT_REFLECTION`, `RUNTIME_WITHOUT_CLASS_FOR_NAME`, `RUNTIME_IS_NOT_USED` (nothing outside `runtime` may depend on it).

### Native-image smoke test (M05-T03, extended in M05-T02)

- Module `maegae-native-smoke`: test-only, not published, not part of `build` or `check`. The task `:maegae-native-smoke:nativeSmokeTest` runs the app on the JVM, builds a native image with `native-image` (flags `--no-fallback` and `--exact-reachability-metadata`), runs it, and fails if the two outputs differ. The GraalVM home comes from `-PgraalvmHome`, then `GRAALVM_HOME`, then `JAVA_HOME`. No hand-written native-image configuration exists. Do not add any, because it would hide problems.
- `--no-fallback` is deprecated and has no effect in this GraalVM version (agent-reported). It was kept.
- The app builds `ToolRegistry.of(...)` over three providers (top-level, nested, generic), lists the tools, runs Tool calls, and checks the duplicate-name error. JVM output and native output were identical.
- Results: local run on macOS arm64 with GraalVM CE for JDK 25 from sdkman (agent-reported), image about 12.3 MiB, build 24 to 34 seconds. CI: the workflow `.github/workflows/native-smoke.yml` passed on `ubuntu-latest` with `graalvm-community` and Java 25 (user-reported) for the T03 PR. CI result for the M05-T02 PR (registry path): build and native-smoke passed (user-reported, not checked by the assistant or the agent).
- Negative control: without the service file, the native image failed with a `MissingResourceRegistrationError` (the exact-metadata mode tracks the service file as a resource). The registry-path negative control was run on the JVM only, where the registry throws the "No Tool binding found" error. It was not repeated in native mode.
- Verified scope: ServiceLoader discovery and Tool execution in the smoke app. **Not covered: Netty, virtual threads, SLF4J, other operating systems, other GraalVM versions.** The README says this is not general native-image support. Keep it so.

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
| M04-T06 Integration & Hardening | Complete, merged (user-reported). PR #22, merge commit d1239b5 |
| M05-T01 Tool binding generation | Complete, merged (user-reported). PR #24, merge commit 38f8ba3 |
| M05-T03 Native-image smoke test | Complete, merged (user-reported). PR #25, merge commit e889f49 |
| M05-T02 Tool registry | Complete, merged (user-reported). PR #27, merge commit 311f87a |
| M06–M10 | Agreed at milestone level only (section 11) |

Commits on the feature branches (agent-reported; the merge method may have changed these hashes):
- T05: `c629cb4` (hash-based class names), `4e070af` (adapter generation), `a17a557` (test hardening).
- T06: `cf73f0a` (processor claims Tool and Arg), `3cf1f05` (end-to-end pipeline tests and README), `da4d12d` (annotation-claiming documentation and Gradle usage in README).

Test counts after M05-T02 (agent-reported): core 402, processor 112, netty 35, no failures or skips. Use the numbers from the clean build that you ran for this edit if they differ.

M04 was planned with seven tasks. The metadata and schema tasks were merged into one, so M04 has six.

M05 order was T01, T03, T02. T03 (native smoke test) was done before the registry on purpose: if ServiceLoader had failed in a native image, the design would have changed to option C (section 9a).

### T06 results (agent-reported)

- `PipelineHarness` and `ToolPipelineTest` (16 cases from 6 methods) compile with `-Xlint:all -Werror` and use `--processor-path`.
- Consistency (item A): one loop over 28 Tools driven by the generated schema checks valid values, wrong JSON types, `required` list behavior, names and descriptions. Mutation checks M1–M4 (type mapping, required list, optional getter, description) were killed by this test. A swap of same-typed parameter names (M5) is caught only by the older T05 test `runsTheMatchingMethodWithArgumentsMappedByName`.
- Six providers (public, package-private, nested, default package, `a.Tools`, `b.Tools`) compile together. All define a Tool named `who`, and generation does not collide. Duplicate detection across providers is registry work and is not implemented.
- A valid provider plus an invalid provider in one compilation: one error, the invalid provider gets no files, the valid provider still gets both files, javac writes no `.class` files. This matches the current code. It is not a specified contract.
- Another processor that generates a provider in round one works in both processor orders, with each provider generated once.
- Generic providers (`Box<T extends Number>`, `Pair<A, B extends Comparable<B>>`) compile and run with no warnings.
- Null returns for Boolean, Byte, Short, Long, Float, Double and BigInteger give `ToolOutput.empty()`.

### Real Gradle consumer check (agent-reported, scratch project, not committed)

Used a composite build (`includeBuild`) with explicit `dependencySubstitution`, because the repository sets no group or version. `compileJava` had `-Xlint:all -Werror`.

| Declarations | Result |
|---|---|
| `annotationProcessor` (processor) + `implementation` (core) | Compiles. Recommended. |
| `annotationProcessor` (processor) + `compileOnly` (core) | Compiles, but the runtime classpath is empty, so the generated adapters cannot run. |
| `annotationProcessor` only | Fails: package `dev.nexcraft.maegae.annotation` does not exist. |

Gradle resolves the processor's runtime variant, so core reaches the annotation processor path automatically. Generated files compiled under `-Xlint:all -Werror` in the real build. Not verified: published artifacts (no `maven-publish` setup exists), POM transitive scopes, a non-macOS host. Coordinates and versions in the README are provisional.

---

## 11. Next Step — Server Runtime Milestones

Agreed decomposition (2026-11-13). The task split inside each milestone is not planned yet.

| Milestone | Content |
|---|---|
| M06 | Dispatcher: `initialize`, version negotiation, `tools/list`, `tools/call`, error mapping per section 9, conversion of `ToolOutput` to `CallToolResult` |
| M07 | Execution: virtual threads, timeouts, concurrency limits |
| M08 | Netty stateless Streamable HTTP transport |
| M09 | Public API (`Maegae.server()...listen()`), example module, real MCP client interoperability, native-image smoke test extended to Netty, virtual threads and SLF4J |
| M10 | MCP 2026-07-28 support |

M06 design notes:
- The dispatcher lives in `dev.nexcraft.maegae.runtime`. Because `TRANSPORT_INDEPENDENCE` does not allow `transport` classes to use `runtime` (section 9a), define a small interface in `transport` that the dispatcher implements. Do not edit the rule to get around this without an explicit decision.
- Send `content: []` for void and null returns (section 4).
- The input schema in `ToolDefinition` is a JSON string. Insert it into `tools/list` as a JSON object, not as a string.
- Keep the list of negotiable protocol versions configurable. v1 baseline is 2025-11-25.
- Check the `ImplementationInfo` fields against the 2025-11-25 schema (section 4, "Still not verified").
- `ToolArgumentException` and Tool exceptions map to Tool execution errors, and unknown Tool names and malformed `tools/call` structures map to JSON-RPC -32602 (section 9).

Later or separate: `structuredContent` and `outputSchema` generation (object root is required in 2025-11-25, so scalar returns need wrapping); publishing to Maven Central; README roadmap checkboxes.

---

## 12. Open Items and Risks

- Native-image support is verified only for the smoke app (section 9a). Netty 4.2, virtual threads, SLF4J, other operating systems and other GraalVM versions are not verified. Check them in M09. The README must keep saying this is not general native-image support.
- The registry-path negative control was run on the JVM only, not in native mode.
- `--no-fallback` has no effect in the current GraalVM version (deprecated). Consider removing it later.
- The README names GraalVM versions in two forms (`native-image 25.0.4.1.1` and `GraalVM CE 25.4.4.1.1+1.1` appear in agent reports). Check that the README states the right one. Low priority.
- The aggregating processor reprocesses all annotated classes when one changes. It was not confirmed from logs that Gradle ran incrementally.
- Gradle usage was verified only through a composite build (scratch project). Published artifacts, POM scopes and non-macOS hosts are not verified. Coordinates in the README are provisional.
- The processor claims @Tool and @Arg (section 6). Processors that run after Maegae do not see these annotations.
- A valid provider keeps its generated files when another provider in the same compilation is invalid. This is current behavior, not a specified contract.
- Local classes are not discovered (section 6). Cross-provider duplicate Tool names are detected only by the runtime registry, not at compile time.
- Provider match is exact. Subclasses, anonymous classes and JPMS (`provides` in module-info) are not supported or must be written by hand.
- Stateless mode has no server-initiated requests (no progress, sampling, or elicitation). Document this when the runtime exists.
- Unverified M03 details: section 4, "Still not verified".

---

## 13. Development Workflow

1. Discuss scope before implementation when architecture decisions are unresolved. Resolve only decisions essential to the task.
2. One clearly bounded task at a time. Lower-level dependencies first.
3. Do not change unrelated components. Preserve existing names unless a rename is approved.
4. No speculative abstractions.
5. Verify the full build before marking a task complete. Report actual status, not assumed status.
6. Update the README support matrix when capabilities change.

Coding agent prompts (English) include: objective, scope, architecture constraints, fixed decisions, implementation requirements, testing expectations, acceptance criteria, verification commands, and a completion report. Lessons from M04 and M05:
- Start the prompt with "Implement this task now. Do not summarize any previous task." An agent once answered with a summary of the previous task instead of implementing (a usage limit was also involved).
- Add "stop and report before changing a production contract". In T06 the agent changed the processor's claiming behavior on its own, and the user had to approve it afterward.
- Ask the agent for evidence (file, test method, assertion) when reviewing its report. Do not accept counts alone.
- Review reports for claims that were not verified, for tests that were silently dropped, and for tests that do not reach the code they claim to test (for example, the codec rejecting an input before the code under test sees it).
- Use mutation checks on safety-critical code. Remove a check, run the tests, and require a failure. If a mutation survives, add a test or document why the check is redundant. Require `git status` to show a clean tree afterward (an agent once stacked mutations because of a wrong restore path).
- Real consumer builds find problems that a test harness hides (for example the `-Xlint` failure found in T06).
- The agent sandbox can block Gradle writes to `~/.gradle`. The agent may need to run Gradle outside the sandbox.
- Start every task in a new agent session. Twice an agent that kept an old context answered with the report of the previous task and did not implement the new one. If a report has no files, commits or tests of the new task, the task was not done.
- The agent sandbox may block `git fetch` and `git pull` (github.com:22). The owner must check that the branch is based on the current `origin/main`.
- Before choosing a package for new code, read the ArchUnit rules. Two M05 decisions (the SPI package and the registry package) were wrong because the rules were not read first. The Preflight now asks the agent to read the rules, and it must stop and report instead of weakening a rule.
- A native-image smoke test with no hand-written configuration and a negative control (remove the service file, expect failure) gives real evidence. Do the riskiest design check first (M05-T03 before M05-T02).
- Write README sentences as facts only. Do not describe who reported or who ran a check.

Testing policy — no AI slop. Do not add tests only for coverage. Avoid trivial assertions, redundant cases, excessive mocking, brittle implementation-detail tests, and tests that only check that a class exists. Prefer real compiler integration tests, generated-code execution tests, deterministic behavioral assertions, boundary and failure-path tests, and architecture regression tests.

Git: the user works with feature branches, PRs, and merges. Do not force-push, reset, or discard commits. Do not push to main directly. The agent does not push unless the user asks.

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

You are continuing development of Maegae. M01 to M05 are done and merged. The server runtime is not implemented.

Before you plan M06:
1. Do not assume details marked "agent-reported" are exact. Inspect the repository or ask the user for evidence.
2. Read the ArchUnit rules in maegae-core (`CoreArchitectureRules`) before choosing packages for new code. Section 9a lists the known rules.
3. Verify the points in section 4, "Still not verified", before the dispatcher design.

Then discuss the M06 task split with the user. Section 11 gives the agreed milestone list and design notes. Resolve only the decisions that are essential for the first task. Write coding-agent prompts in English. They must start with "Implement this task now. Do not summarize any previous task." and include Preflight, scope, fixed decisions, work items, test rules, verification, acceptance criteria and a completion report. Tell the agent to stop and report before it changes a production contract or an ArchUnit rule.

Do not claim runtime capabilities are complete until verified. Do not claim native-image support beyond section 9a. Update this document after each milestone.

Current checkpoint: M05 complete and merged. M06 is next.
