# Gap Analysis: Developer Ergonomics & DX

An analysis of the Developer Experience (DX) and developer ergonomics of the `gleamunison-cf` workspace, detailing the current state, gaps, and improvements under Rich Hickey's principles.

---

## 1. Ergonomic Decomplection

We unbraid the developer workflow into three distinct stages:
1. **Compilation Loop**: Building and compiling the GleamUnison source code to WASM.
2. **Local Execution Loop**: Running Wrangler local development, tracking logs, and hot-reloading.
3. **Verification Loop**: Running test cases, checking formatting, and linting.

---

## 2. Feature Set Comparison: Current vs. Ideal DX

| Dimension | Current State | Ideal Ergonomic State |
| :--- | :--- | :--- |
| **WASM Build Pipeline** | Manual copy from separate `gleamwasm` repository | Monorepo structure or automated compilation/copy scripts |
| **Local Dev Lifecycle** | `bb dev` (starts server) | `bb dev` with hot-restarting and automatic log formatting |
| **Verification Loop** | `bb test` (manual run) | `bb test` with `--watch` mode and automatic port allocation |
| **Source Code Visibility** | Black-box WASM files in `src/` | Direct visibility into the Gleam source code inside the repo |
| **Port Conflicts** | Static port `8793` (throws error if bound) | Dynamic free-port discovery |

---

## 3. Explaining Key Ergonomic Gaps

### A. The Cross-Repo Compilation Loop (Major Friction)
* **The Gap**: The source code of GleamUnison (4,271 lines of Gleam) resides in a separate repository (`gleamwasm`). Developers modifying the runtime must compilation-test in that repo, locate the compiler output, and manually copy `gleamunison_sc.wasm` to `gleamunison-cf/src/`.
* **Hickey Principle**: *Decomplection of Source and Artifact.* A developer should not have to manually bridge two independent code topologies to see their changes take effect.

### B. Verification Port Resilience
* **The Gap**: The test script hardcodes port `8793`. If another process is using port `8793`, the wrangler process fails to bind, and the test runner times out after 15 seconds.
* **Hickey Principle**: *Robustness over Fragility.* Software should dynamically adapt to its physical context (like free ports) rather than failing due to place-oriented constraints.

---

## 4. Complexity vs. Utility Matrix

| Improvement Opportunity | Complexity (1-10) | Utility (1-10) | Description |
| :--- | :--- | :--- | :--- |
| **1. Dynamic Port Selection in Tests** | 3 / 10 | 7 / 10 | Let the Babashka script find a random free port and pass it to Wrangler, preventing conflicts. |
| **2. Automatic WASM Copy script** | 2 / 10 | 6 / 10 | A script or symlink to auto-copy compiled WASM from the build folder if adjacent. |
| **3. Test Watcher (`bb test --watch`)** | 4 / 10 | 8 / 10 | Watches files and automatically triggers integration tests on changes. |

---

## 5. Actionable Recommendations

### Recommendation 1: Dynamic Port Allocation for Tests (Priority 1)
* Refactor `test.clj` to find a random available local port, boot Wrangler on that port, and run assertions. This completely avoids test run failures due to occupied ports.

### Recommendation 2: Add a WASM Import Sync Script (Priority 2)
* Write a small Babashka helper task `bb sync-wasm` that locates the adjacent `gleamwasm` build directory and copies the fresh `.wasm` output to `src/` automatically.
