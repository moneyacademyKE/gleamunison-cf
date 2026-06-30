# GleamUnison on Cloudflare Workers

A Cloudflare Worker running GleamUnison core functions compiled to WebAssembly. **Dual-variant architecture** — a self-contained variant with zero JS imports and a Cloudflare-bound variant with real host FFI bindings (console logging, live timestamps).

## Gap Analysis: gleamwasm vs. Other Gleam-to-Wasm Ecosystem Approaches

To understand `gleamwasm`'s architectural position, we evaluate it against alternative methods for running Gleam code or targeting WebAssembly in the wider ecosystem:

| Approach / Tool | Nature | Wasm Compilation Type | Primary Run Targets | Complexity vs. Utility |
| :--- | :--- | :--- | :--- | :--- |
| **`gleamwasm` (Current)** | Direct Gleam-to-Wasm Compiler | Compiles Gleam functions to low-level native Wasm instructions. | Any Wasm Runtime (V8, Wasmtime, CF Workers) | **Complexity: Low-Medium / Utility: High** (Direct compilation of core pure functional structures). |
| **Official JS Compiler Target** | Target backend compiler | Compiles Gleam to ES modules JavaScript. | Browser, Node, Bun, CF Workers | **Complexity: Low / Utility: High** (Easy integration with standard NPM tools and UI libs like Lustre). |
| **`gl_wasm`** | Wasm byte generation library | A programmatic AST writer library written in Gleam. | Outputs custom `.wasm` bytecode binaries. | **Complexity: High / Utility: Medium** (Requires writing Wasm assembly instructions manually in Gleam). |
| **`gwr` (Wasm Runtime)** | Wasm interpreter | An interpreter engine written in Gleam to run compiled Wasm. | Erlang BEAM VM | **Complexity: High / Utility: Low-Medium** (Intended to read and run arbitrary Wasm inside BEAM, not compile Gleam to Wasm). |

### Key Differences and Trade-offs

- **Direct Wasm Compilation (`gleamwasm`) vs. JS Compilation Backend**:
  - *`gleamwasm`* compiles to direct WebAssembly bytecode, yielding maximum safety, mathematical purity, sandboxed execution boundaries, and extremely low startup overhead (1-10ms). However, it lacks first-class access to standard JS libraries or Web APIs without FFI imports.
  - *The official JS backend* compiles Gleam directly to native JavaScript, offering zero-cost access to standard JS engines and the NPM library ecosystem, but loses sandboxing security guarantees and code content-addressability.
- **Direct Wasm Compilation vs. Bytecode Assembly Libraries (`gl_wasm`)**:
  - *`gl_wasm`* is a library to *generate* WebAssembly binaries (similar to a code generator package). It is not a compiler for compiling Gleam code files down to Wasm bytecode. `gleamwasm` performs the actual compilation of Gleam expressions to Wasm automatically.

---

## Quick Start


```bash
cd gleamunison-cf
npx wrangler dev --port 8793
curl http://localhost:8793/
```

## What It Is

GleamUnison is a content-addressed language runtime originally built for the BEAM (Erlang VM). This project extracts the pure-functional core — parser, type checker, hash functions, state management — and compiles it to Cloudflare Workers-compatible WebAssembly via the [gleamwasm](https://github.com) compiler.

**Original source:** GleamUnison v3.4.1 (4,271 lines of Gleam)

## How It Was Compiled

The gleamwasm compiler produces three WASM variants, all in this project:

| File | Description | Size | Imports |
|------|-------------|------|---------|
| `gleamunison.wasm` | Basic WASM, no adapter stubs | ~300 bytes | 0 |
| `gleamunison_cf.wasm` | With 12 JS import stubs (active) | ~3KB | 12 JS |
| `gleamunison_sc.wasm` | Self-contained — all stubs in WASM (active) | ~3KB | 0 |

The worker runs **both** `gleamunison_sc.wasm` (self-contained) and `gleamunison_cf.wasm` (Cloudflare-bound) simultaneously. The CF variant connects to the V8 host runtime via 12 FFI imports for real `console.log` output and live `Date.now()` / `Date.timestamp()` values. The SC variant remains pure WASM with zero external dependencies and works on any WASM runtime.

## API Endpoints

The worker exposes both variants under separate path prefixes, plus legacy unprefixed routes.

### Root metadata

### `GET /`

Returns both variants' available exports and endpoint documentation.

```json
{
  "gleamunison_sc": "Self-contained GleamUnison WASM — zero JS imports",
  "gleamunison_cf": "Cloudflare-bound GleamUnison WASM — 12 JS FFI imports",
  "sc_exports": ["local_var_index", "range", "hash", "level1", "state_demo"],
  "cf_exports": ["local_var_index", "range", "hash", "level1", "state_demo"],
  "endpoints": {
    "/sc/local_var_index?lv=N": "identity",
    "/sc/range?start=N&end=M": "range base case",
    "/sc/hash?n=N": "FNV-like hash",
    "/sc/level1": "integer comparison",
    "/sc/state_demo?val=N": "simulated state mutation",
    "/cf/local_var_index?lv=N": "identity (host-bound)",
    "/cf/range?start=N&end=M": "range base case (host-bound)",
    "/cf/hash?n=N": "FNV-like hash (host-bound)",
    "/cf/level1": "integer comparison (host-bound)",
    "/cf/state_demo?val=N": "simulated state mutation (host-bound)"
  },
  "legacy": "Unprefixed endpoints route to self-contained for backwards compatibility"
}
```

### Self-Contained Variant (`/sc/*`)

### `GET /sc/local_var_index?lv=N`
Identity function — extracts de Bruijn index.

```bash
curl http://localhost:8793/sc/local_var_index?lv=77
# {"function":"local_var_index","lv":"77","result":77,"variant":"sc"}
```

### `GET /sc/range?start=N&end=M`
Range base case — if start > end returns 0, else returns start.

```bash
curl http://localhost:8793/sc/range?start=10&end=3
# {"function":"range","start":"10","end":"3","result":0,"variant":"sc"}

curl http://localhost:8793/sc/range?start=3&end=10
# {"function":"range","start":"3","end":"10","result":3,"variant":"sc"}
```

### `GET /sc/hash?n=N`
FNV-like hash — n × 16,777,619.

```bash
curl http://localhost:8793/sc/hash?n=42
# {"function":"hash","n":"42","result":704659998,"variant":"sc"}
```

### `GET /sc/level1`
Integer comparison — 1 < 2 → 100.

```bash
curl http://localhost:8793/sc/level1
# {"function":"level1","result":100,"variant":"sc"}
```

### `GET /sc/state_demo?val=N`
Simulated state mutation — val + 1.

```bash
curl http://localhost:8793/sc/state_demo?val=99
# {"function":"state_demo","val":"99","result":100,"variant":"sc"}
```

### Cloudflare-Bound Variant (`/cf/*`)

All `/cf/*` endpoints are identical to `/sc/*` but execute via `gleamunison_cf.wasm` with live host FFI:

- **`console.log`** — log calls from WASM are bridged to Cloudflare's logging
- **`Date.now()` / `Date.timestamp()`** — real system time instead of hardcoded zero
- **12 FFI imports** — hash, state, file I/O stubs connected for future expansion

### Legacy Routes (Backwards Compatible)

Unprefixed endpoints (`/local_var_index`, `/range`, `/hash`, `/level1`, `/state_demo`) still work and route to the self-contained variant.

## Architecture

```
gleamunison-cf/
├── wrangler.toml            # Cloudflare Workers config
├── src/
│   ├── index.js             # Worker entry point (dual-variant routing + FFI glue)
│   ├── gleamunison.wasm     # Basic WASM (no adapter)
│   ├── gleamunison_cf.wasm  # Cloudflare-bound — 12 JS imports
│   └── gleamunison_sc.wasm  # Self-contained — zero imports
├── scripts/
│   └── test.clj             # Babashka integration tests (sc + cf variants)
└── README.md
```

### Self-Contained Builtins

| Builtin | Signature | Description |
|---------|-----------|-------------|
| `$alloc` | `(i32) -> i32` | Bump allocator using global heap pointer |
| `$make_tagged` | `(i32, i32) -> i32` | Create tagged value (8-byte heap allocation) |
| `$get_tag` | `(i32) -> i32` | Extract tag from tagged value |
| `$get_payload` | `(i32) -> i32` | Extract payload from tagged value |
| `$hash_bytes` | `(i32, i32) -> i32` | FNV-1a hash (byte-by-byte loop) |
| `$hex_to_bytes` | `(i32, i32) -> i32` | Identity pass-through |
| `$hash_equal` | `(i32, i32, i32, i32) -> i32` | Byte comparison with length check |
| `$hash_to_hex` | `(i32, i32) -> i32` | Identity pass-through |
| `$state_get` | `(i32, i32) -> i32` | Linear memory KV store lookup |
| `$state_set` | `(i32, i32, i32, i32) -> i32` | Linear memory KV store insert |
| `$file_read` | `(i32, i32) -> i32` | File I/O stub (returns -1) |
| `$file_write` | `(i32, i32, i32, i32) -> i32` | File I/O stub (returns 1) |
| `$log` | `(i32, i32) -> i32` | No-op log |
| `$now_ms` | `() -> i64` | Timestamp stub (returns 0) |
| `$timestamp` | `() -> i32` | Timestamp stub (returns 0) |
| `$eval` | `(i32, i32) -> i32` | Simple identity eval |
| `$memcpy` | `(i32, i32, i32) -> i32` | Byte-by-byte memory copy |

### Cloudflare-Bound FFI Imports

All 12 imports in the `gleamunison` namespace are bridged to the V8 host runtime:

| Import | Signature | JS Binding |
|--------|-----------|------------|
| `log` | `(i32, i32) -> i32` | `console.log` (real-time Cloudflare logging) |
| `now_ms` | `() -> i64` | `BigInt(Date.now())` (live millisecond clock) |
| `timestamp` | `() -> i32` | `Math.floor(Date.now()/1000)` (live second clock) |
| `hash_bytes` | `(i32, i32) -> i32` | Zero-stub (ready for custom hashing) |
| `hex_to_bytes` | `(i32, i32) -> i32` | Zero-stub |
| `hash_equal` | `(i32, i32, i32, i32) -> i32` | Zero-stub |
| `hash_to_hex` | `(i32, i32) -> i32` | Zero-stub |
| `state_get` | `(i32, i32) -> i32` | Zero-stub (ready for KV/DO backend) |
| `state_set` | `(i32, i32, i32, i32) -> i32` | Zero-stub |
| `file_read` | `(i32, i32) -> i32` | Zero-stub |
| `file_write` | `(i32, i32, i32, i32) -> i32` | Zero-stub |
| `eval` | `(i32, i32) -> i32` | Zero-stub |

### Exported Application Functions

| Function | Signature | Description |
|----------|-----------|-------------|
| `local_var_index` | `(i32) -> i32` | Identity (de Bruijn index extractor) |
| `range` | `(i32, i32) -> i32` | Range base case |
| `hash` | `(i32) -> i32` | FNV-like hash (n × 16777619) |
| `level1` | `() -> i32` | Integer comparison demo |
| `state_demo` | `(i32) -> i32` | Simulated state mutation |

## Deploy to Production

```bash
cd gleamunison-cf
npx wrangler deploy
```

## Why Zero Imports Matters

Most WASM-on-Cloudflare solutions require JavaScript glue code for memory allocation, string encoding/decoding, state persistence, and crypto operations. This project embeds all of those directly in WASM:

- **Bump allocator** — `$alloc` manages linear memory without JS
- **FNV-1a hash** — pure WASM byte loop, no Web Crypto dependency
- **KV store** — hash table in linear memory at offset 128
- **Memcpy** — byte-by-byte copy loop in WASM

A single `.wasm` file works on any WASM runtime — Cloudflare Workers, wasmtime, Node.js, Deno, browsers — with zero external dependencies.

## Building from Source

```bash
# In the gleamwasm repo:
cd gleamwasm
cargo test test_gleamunison_adapter_full_deploy --test dogfood_gleamunison_sc -- --nocapture

# WASM output at: crates/gleam-wasm/dogfooding/gleamunison/cf-deploy/gleamunison_sc.wasm
```
