# Use Cases, Suitability & Alternatives

This document performs a gap analysis on the suitability of deploying the **GleamUnison WebAssembly core on Cloudflare Workers** versus alternative development stacks. We evaluate their trade-offs under the core principles of decomplection, complexity-reduction, and platform compatibility.

---

## 1. Use Cases & Suitability Matrix

We analyze the suitability of the current GleamUnison WASM + Workers stack across several core use cases:

| Use Case | Suitability | Rationale / Hickey Analysis |
| :--- | :--- | :--- |
| **Edge-Native Code Evaluation (FaaS)** | **High** | Code is fetched, parsed, type-checked, and run in sandboxed V8 isolates with 0ms instantiation overhead. |
| **Content-Addressable Distributed Systems** | **High** | Identical function hashes resolve to identical execution outcomes regardless of the regional worker host. |
| **High-Throughput CRUD API Routes** | **Low** | Passing JSON payloads across the JS-to-WASM boundary introduces serialization overhead compared to native JS. |
| **Distributed State Persistence / Coordination** | **Medium** | Transient WASM linear memory requires bridging to external stores (KV/D1/R2) to persist state across isolates. |

---

## 2. Feature Set Comparison vs. Alternatives

We compare the **Current Stack** against three primary alternatives:
1. **Standard JS/TS on Cloudflare Workers**: Baseline serverless stack.
2. **Rust on Cloudflare Workers (via `workers-rs` / WASM)**: High-performance systems-level edge stack.
3. **Elixir / BEAM VM Hosting (e.g. Fly.io)**: Traditional stateful actor hosting.

### Feature Differences Table

| Feature Dimension | GleamUnison WASM on Workers (Current) | JS/TS on Workers (Baseline) | Rust WASM on Workers (`workers-rs`) | Elixir/BEAM on Fly.io (Elixir) |
| :--- | :--- | :--- | :--- | :--- |
| **Execution Safety** | Pure-functional, sandboxed | Dynamic, JS-runtime sandboxed | Memory-safe, compiled sandboxed | Actor-isolated, crash-resilient VM |
| **Cold-Start Instantiation** | **1–10ms** | 20–150ms | 5–20ms | ~500ms–2s (container start) |
| **Concurrency Model** | Single-threaded per isolate | Single-threaded per isolate | Single-threaded per isolate | Preemptive green threads (Actors) |
| **State Lifespan** | Transient (isolate-reused) | Transient (isolate-reused) | Transient (isolate-reused) | Persistent (actor process loops) |
| **Ecosystem Libraries** | Restricted to compiled Gleam core | Direct access to npm / JS ecosystem | Crates.io Rust ecosystem | Hex.pm / Erlang OTP libraries |
| **Serialization Overhead** | Medium (JS host to WASM bridge) | **Zero (Native JS)** | High (JS host to WASM bridge) | Zero (Native VM structures) |

---

## 3. Explaining Key Feature Differences

### A. Execution Safety and Sandbox Boundaries
* **The Difference**: The current GleamUnison WASM runtime runs as a pure mathematical sandbox inside V8. Unison's content-addressability guarantees that functions cannot have side-effects outside of explicitly declared algebraic effects.
* **Hickey Decomplection**: In standard JS/TS, code evaluation (e.g. `eval()` or dynamic imports) is complected with system-level security risks. The WASM sandbox unbraids code execution from host security, letting us execute user-defined logic safely.

### B. Cold Start & Instantiation Latency
* **The Difference**: While JS Workers suffer from JIT warmup latency, WASM modules instantiate in **1-10ms**. Traditional BEAM containers (Fly.io) take hundreds of milliseconds to spin up virtual environments.
* **Hickey Decomplection**: Running on Cloudflare's V8 isolates separates the hosting topology from the runtime. We do not manage containers (decomplecting DevOps), yet we avoid the heavy JS engine warmup times.

### C. Concurrency: Isolate vs. Actor VM
* **The Difference**: Elixir/BEAM excels at holding millions of concurrent, stateful actors that communicate via message-passing. Workers are stateless request/response engines.
* **The Trade-Off**: If the application requires complex real-time websocket synchronization or stateful actor lifecycles, Fly.io (Elixir) is highly suitable. For massive scale, zero-maintenance global routing, Cloudflare Workers wins.

---

## 4. Complexity vs. Utility

Evaluating each stack choice by implementation complexity (learning curve, glue code, build pipeline) versus runtime utility (performance, scalability, correctness guarantees):

| Stack Choice | Complexity (1-10) | Utility (1-10) | Trade-Off Description |
| :--- | :--- | :--- | :--- |
| **GleamUnison WASM on CF (Current)** | 7 / 10 | 8 / 10 | High correctness and code-addressability at low latency, but requires compilation to WASM and JS-bridge glue. |
| **Standard JS/TS on CF** | 2 / 10 | 5 / 10 | Fast development and zero bridge overhead, but lacks content-addressability and pure mathematical sandboxing. |
| **Rust WASM on CF** | 6 / 10 | 7 / 10 | Excellent performance for system tasks, but larger binary footprints and higher developer overhead. |
| **Elixir / BEAM on Fly.io** | 5 / 10 | 9 / 10 | Native stateful actors and OTP framework, but requires running dedicated server VMs with cold starts. |

---

## 5. Actionable Recommendations

### Recommendation 1: Use GleamUnison WASM for Content-Addressed Computations
* **Best Fit**: Choose this stack for building distributed computation grids, serverless executors (FaaS), and systems requiring cryptographic execution guarantees (e.g. smart contracts, pure-functional pipelines).

### Recommendation 2: Use Standard JS/TS for I/O-Bound Orchestration
* **Best Fit**: If you are simply fetching from a database, validating an API token, or proxying assets, bypass WASM to eliminate serialization/bridging overhead and stick to native JS/TS.

### Recommendation 3: Use Elixir/BEAM for Long-Lived, Connected Stateful Systems
* **Best Fit**: For applications requiring persistent collaborative workspaces (e.g., Figma-like state sync, chat rooms, multiplayer games), host standard BEAM/Elixir on VM topologies rather than serverless isolates.
