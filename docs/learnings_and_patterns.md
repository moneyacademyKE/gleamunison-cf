# Learnings and Patterns

## Learnings

### 1. Cloudflare Workers WebAssembly Instantiation Mechanics
* **Wrangler Pre-Compilation**: When importing a `.wasm` file using ES module syntax (e.g., `import wasmSource from "./module.wasm"`), the Wrangler build tool pre-compiles the binary into a `WebAssembly.Module` object.
* **Synchronous Instantiation**: Because the module is already pre-compiled, it can be instantiated synchronously using the standard `new WebAssembly.Instance(wasmSource, importObject)` API. This avoids the need for dynamic compilation (which is banned by Workers' security rules) and allows instantiation to occur instantly.
* **Top-Level Await Limitation**: Cloudflare Workers do not support top-level await in ES module entry points. By using synchronous instantiation, we bypass this limitation completely.

---

## Patterns

### 1. Global-Scoped WASM Instantiation Pattern
To optimize WebAssembly execution on Cloudflare Workers:
* Avoid instantiating the WASM module inside the request (`fetch`) handler.
* Instead, instantiate the module synchronously at the top level of your script.
* This avoids rebuilding the export map and allocating fresh memory per request, dropping instantiation overhead inside the hot path to 0ms.
* **Caveat**: Remember that state stored in WASM linear memory will persist across requests handled by the same V8 isolate.

### 2. Babashka Dev Server Integration Testing Pattern
When writing integration tests for local edge functions:
1. Use `babashka.process/process` to start the local dev server (e.g. `npx wrangler dev`) in a background process.
2. Poll the server's health endpoint with a sleep-loop (using `babashka.http-client`) until it responds with a success status code.
3. Run test cases and assert responses.
4. Always wrap the execution in a `try-finally` block, calling `babashka.process/destroy-tree` on the process handle inside the `finally` block to prevent orphaned background processes and port binding issues.
