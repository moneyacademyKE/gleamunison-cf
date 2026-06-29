# Project Playbook: gleamunison-cf

A playbook detailing the architecture, coding standards, and workflows for this repository.

## Architecture Guidelines

* **Pure WebAssembly Core**: The runtime is compiled using the `gleamwasm` compiler. The active target is `gleamunison_sc.wasm` which contains no JS imports (zero-import self-contained architecture).
* **Global Instantiation**: Always instantiate the Wasm modules in the global scope using synchronous `new WebAssembly.Instance(wasmSource, {})` to ensure maximum performance and low startup latency.
* **Transient state safety**: Be mindful that WASM linear memory persists across requests within the same V8 isolate. Code must be written so that multiple requests do not pollute shared states.

## Coding and Testing Standards

* **File Size Constraint**: Maintain all files under `250` lines of code to encourage high cohesion and low coupling.
* **No npm Directives**: Always use `npx` for executing tools (e.g. `npx wrangler`) to avoid local dependency sprawl.
* **Babashka Scripting**: All auxiliary scripts, task automation, and build steps must use Clojure/Babashka instead of Python or shell scripts.

## Task Runner Commands (Developer Ergonomics)

This repository includes a `bb.edn` task runner to simplify local development:

* **List Tasks**: List all available tasks.
  ```bash
  bb tasks
  ```
* **Local Development**: Run Wrangler dev server locally on port 8793.
  ```bash
  bb dev
  ```
* **Verify Work**: Run the automated integration test suite.
  ```bash
  bb test
  ```
* **Deploy to Production**: Deploy the Cloudflare Worker.
  ```bash
  bb deploy
  ```

