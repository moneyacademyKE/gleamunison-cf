# Gap Analysis: GleamUnison WASM Core vs. SolidJS (SolidStart)

This document performs a Rich Hickey Gap Analysis comparing the architectural paradigm of deploying a **GleamUnison WebAssembly core** versus building with **SolidJS (SolidStart)** on Cloudflare Workers.

---

## 1. Paradigm Decomplection

To evaluate these stacks, we must unbraid their core architectural concerns:
1. **Primary Concern**: GleamUnison is complected with *expression evaluation, type checking, and content-addressed execution*. SolidJS is complected with *reactive DOM updates, UI rendering, and full-stack web structure*.
2. **Reactivity Model**: SolidJS uses *fine-grained, push-based signals* where state updates trigger minimal DOM modifications. GleamUnison uses *pure-functional evaluations* where state is passed as immutable values and updated in linear memory.
3. **Compilation Boundary**: SolidJS compiles JSX directly into efficient JS instructions executing natively on the V8 engine. GleamUnison compiles Gleam to WASM instructions, which execute inside a sandboxed VM boundary.

---

## 2. Feature Set Comparison

| Feature Dimension | GleamUnison WASM on Workers | SolidJS (SolidStart) on Workers |
| :--- | :--- | :--- |
| **Primary Output** | Evaluated terms / AST hashes | Reactive HTML / Interactive UI |
| **Execution Layer** | WASM virtual machine (inside V8) | Native JavaScript (V8 Engine) |
| **Reactivity** | Functional state transformation | Fine-grained reactive signals |
| **DOM Interaction** | Indirect (Requires JS bridge) | **Direct and highly optimized** |
| **Cold Start** | **1–10ms** | 10–30ms |
| **Ecosystem & Glue** | Custom FFI / `gleamwasm` | Vite / NPM ecosystem / Web standards |
| **Data Binding** | Memory lookup (KV offset) | Native JSON / Cloudflare Bindings |

---

## 3. Explaining Key Feature Differences

### A. Reactivity vs. Purity
* **SolidJS**: Built around **Signals** (`createSignal`). Changes propagate immediately to dependent computations and DOM elements. This makes SolidJS exceptionally efficient for interactive interfaces.
* **GleamUnison**: Built around **pure functional transformations** (immutability). Instead of pushing changes reactively, it takes an input, runs it through type-checking and evaluation, and returns a new representation.
* **Hickey Principle**: *Purity is simple.* GleamUnison's state management is simple because it does not have the complex temporal coordination of reactivity graphs; however, it lacks the ease of UI binding.

### B. Compilation Targets and Bridges
* **SolidJS**: Compiles to standard ES modules, running directly on the Workers v8 runtime. It has zero-cost access to Cloudflare bindings (`env.DB`, `env.KV`).
* **GleamUnison**: Compiles to `.wasm` files. Interacting with the outer Worker environment or DOM requires crossing the WASM-to-JS boundary, causing data serialization overhead.

---

## 4. Complexity vs. Utility Matrix

| Stack Choice | Complexity (1-10) | Utility (1-10) | Rationale |
| :--- | :--- | :--- | :--- |
| **GleamUnison WASM** | 8 / 10 | 9 / 10 (System Logic) | Perfect for cryptographic correctness, building compilers, sandboxing logic, and distributed computing. Very high learning curve. |
| **SolidJS (SolidStart)** | 3 / 10 | 9 / 10 (Application/UI) | Highly productive for full-stack apps, server-side rendering at the edge, and interactive UIs. Low complexity. |

---

## 5. Actionable Recommendations & Trade-offs

### Use GleamUnison WASM if:
* You are building a **language runtime, sandbox executor, parser, or cryptographic validator** at the edge.
* Your priority is strict pure-functional correctness, immutable data structures, and content-addressed execution (code-identity by hash).

### Use SolidJS (SolidStart) if:
* You are building a **user-facing web application, SaaS dashboard, or standard JSON CRUD API**.
* You need native DOM rendering, fast UI interactions, easy routing, and direct integration with NPM libraries and database bindings.

### The Hybrid Synthesis (Best of Both Worlds)
If you need a highly secure computational core *and* a modern UI, the ideal architecture is to **combine them**:
Use **SolidJS** to handle the frontend and routing on Cloudflare, and import/invoke the **GleamUnison WASM module** on the server side to perform core evaluation and type checking safely.
