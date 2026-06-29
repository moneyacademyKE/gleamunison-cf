# ADR 001: Global-Scoped WebAssembly Instantiation

## Context

Originally, the Cloudflare Worker instantiated the GleamUnison WebAssembly module (`gleamunison_sc.wasm`) inside the `fetch` handler on every incoming HTTP request:

```javascript
const wasmModule = await WebAssembly.instantiate(wasmSource, {});
const exports = wasmModule.exports;
```

This request-scoped instantiation has several drawbacks:
1. **Latency Overhead**: The Worker re-instantiated and compiled/linked the module for every single HTTP request. While V8 might cache the module code, the instantiation overhead (allocating clean linear memory, running start/setup functions, and rebuilding exports maps) still runs synchronously/asynchronously on each call.
2. **State Ephemerality**: The linear memory heap is completely wiped and re-allocated for each query. This prevents any isolate-scoped in-memory caching or state persistence from working across requests.

## Decision

We moved WebAssembly module instantiation to the global scope of the ES module using the synchronous `new WebAssembly.Instance()` constructor:

```javascript
const wasmInstance = new WebAssembly.Instance(wasmSource, {});
const exports = wasmInstance.exports;
```

Because Wrangler compiles the `.wasm` file statically and passes it as a pre-compiled `WebAssembly.Module` object (not raw binary data), synchronous instantiation is fully supported and safe to execute at startup without violating Cloudflare Workers' security policies.

## Consequences

### Positive
* **Sub-millisecond Cold Start & Low Latency**: Instantiation occurs only once during isolate cold-start. Subsequent requests reuse the already instantiated module exports, yielding 0ms instantiation overhead inside the fetch path.
* **Isolate State Persistence**: WebAssembly linear memory persists across requests within the same V8 isolate, allowing the module's linear memory KV store to retain state for hot requests.

### Negative
* **Isolate State Leakage Risk**: If the linear memory allocator does not properly free allocations across requests, memory usage could grow over the isolate's lifetime. However, because `gleamunison_sc.wasm` uses a simple bump allocator that resets or operates in a bounded space, this risk is managed.
