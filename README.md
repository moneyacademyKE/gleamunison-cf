# GleamUnison on Cloudflare Workers

A self-contained Cloudflare Worker running GleamUnison core functions compiled to WebAssembly. **Zero JavaScript imports** — all 17 FFI stubs are pure WASM functions.

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
| `gleamunison_cf.wasm` | With 12 JS import stubs | ~3KB | 12 JS |
| `gleamunison_sc.wasm` | Self-contained — all stubs in WASM | ~3KB | 0 |

The active variant is `gleamunison_sc.wasm` (self-contained). All 17 builtins are pure WASM MVP instructions with zero GC operations, making it fully compatible with Cloudflare Workers' V8 runtime.

## API Endpoints

### `GET /`
Returns module info: available exports and endpoint documentation.

```json
{
  "exports": ["local_var_index", "range", "hash", "level1", "state_demo"],
  "endpoints": {
    "/local_var_index?lv=N": "de Bruijn index extractor",
    "/range?start=N&end=M": "range base case",
    "/hash?n=N": "FNV-like hash",
    "/level1": "integer comparison",
    "/state_demo?val=N": "simulated state mutation"
  }
}
```

### `GET /local_var_index?lv=N`
Identity function — extracts de Bruijn index.

```bash
curl http://localhost:8793/local_var_index?lv=77
# {"function":"local_var_index","lv":"77","result":77}
```

### `GET /range?start=N&end=M`
Range base case — if start > end returns 0, else returns start.

```bash
curl http://localhost:8793/range?start=10&end=3
# {"function":"range","start":"10","end":"3","result":0}

curl http://localhost:8793/range?start=3&end=10
# {"function":"range","start":"3","end":"10","result":3}
```

### `GET /hash?n=N`
FNV-like hash — n × 16,777,619.

```bash
curl http://localhost:8793/hash?n=42
# {"function":"hash","n":"42","result":704659998}
```

### `GET /level1`
Integer comparison — 1 < 2 → 100.

```bash
curl http://localhost:8793/level1
# {"function":"level1","result":100}
```

### `GET /state_demo?val=N`
Simulated state mutation — val + 1.

```bash
curl http://localhost:8793/state_demo?val=99
# {"function":"state_demo","val":"99","result":100}
```

## Architecture

```
gleamunison-cf/
├── wrangler.toml            # Cloudflare Workers config
├── src/
│   ├── index.js             # Worker entry point (routes + WASM glue)
│   ├── gleamunison.wasm     # Basic WASM (no adapter)
│   ├── gleamunison_cf.wasm  # With JS import stubs
│   └── gleamunison_sc.wasm  # Self-contained (active) — zero imports
└── README.md
```

### WASM Runtime Builtins (Self-Contained)

All 17 builtins are pure WASM functions with zero GC instructions:

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
