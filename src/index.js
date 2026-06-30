import wasmScSource from "./gleamunison_sc.wasm";
import wasmCfSource from "./gleamunison_cf.wasm";

const cfImports = {
  gleamunison: {
    hash_bytes: (a, b) => 0,
    hex_to_bytes: (a, b) => 0,
    hash_equal: (a, b, c, d) => 0,
    hash_to_hex: (a, b) => 0,
    state_get: (a, b) => 0,
    state_set: (a, b, c, d) => 0,
    file_read: (a, b) => 0,
    file_write: (a, b, c, d) => 0,
    log: (a, b) => {
      console.log("[gleamunison cf]", a, b);
      return 0;
    },
    now_ms: () => BigInt(Date.now()),
    timestamp: () => Math.floor(Date.now() / 1000),
    eval: (a, b) => 0,
  },
};

// Instantiated globally to optimize latency and enable isolate-scoped state persistence.
const scInstance = new WebAssembly.Instance(wasmScSource, {});
const cfInstance = new WebAssembly.Instance(wasmCfSource, cfImports);

const scExports = scInstance.exports;
const cfExports = cfInstance.exports;

const scFnNames = Object.keys(scExports).filter(k => typeof scExports[k] === "function");
const cfFnNames = Object.keys(cfExports).filter(k => typeof cfExports[k] === "function");

function resolveVariant(path) {
  if (path.startsWith("/sc/")) return { variant: "sc", handlerPath: path.slice(3) };
  if (path.startsWith("/cf/")) return { variant: "cf", handlerPath: path.slice(3) };
  return { variant: "sc", handlerPath: path };
}

function getExports(variant) {
  return variant === "sc" ? scExports : cfExports;
}

function route(handlerPath, exports, params, variant) {
  if (handlerPath === "/local_var_index") {
    return json({ function: "local_var_index", lv: params.get("lv"), result: exports.local_var_index(parseInt(params.get("lv")||"0",10)), variant });
  }
  if (handlerPath === "/range") {
    return json({ function: "range", start: params.get("start"), end: params.get("end"), result: exports.range(parseInt(params.get("start")||"0",10), parseInt(params.get("end")||"0",10)), variant });
  }
  if (handlerPath === "/hash") {
    return json({ function: "hash", n: params.get("n"), result: exports.hash(parseInt(params.get("n")||"0",10)), variant });
  }
  if (handlerPath === "/level1") {
    return json({ function: "level1", result: exports.level1(), variant });
  }
  if (handlerPath === "/state_demo") {
    return json({ function: "state_demo", val: params.get("val"), result: exports.state_demo(parseInt(params.get("val")||"0",10)), variant });
  }
  return json({ error: "Unknown endpoint: " + handlerPath }, 404);
}

export default {
  async fetch(request) {
    const url = new URL(request.url);
    const path = url.pathname;
    const params = url.searchParams;

    try {
      if (path === "/") {
        return json({
          gleamunison_sc: "Self-contained GleamUnison WASM — zero JS imports",
          gleamunison_cf: "Cloudflare-bound GleamUnison WASM — 12 JS FFI imports",
          sc_exports: scFnNames,
          cf_exports: cfFnNames,
          endpoints: {
            "/sc/local_var_index?lv=N": "identity",
            "/sc/range?start=N&end=M": "range base case",
            "/sc/hash?n=N": "FNV-like hash",
            "/sc/level1": "integer comparison",
            "/sc/state_demo?val=N": "simulated state mutation",
            "/cf/local_var_index?lv=N": "identity (host-bound)",
            "/cf/range?start=N&end=M": "range base case (host-bound)",
            "/cf/hash?n=N": "FNV-like hash (host-bound)",
            "/cf/level1": "integer comparison (host-bound)",
            "/cf/state_demo?val=N": "simulated state mutation (host-bound)",
          },
          legacy: "Unprefixed endpoints (e.g. /local_var_index) route to the self-contained variant for backwards compatibility.",
        });
      }

      const { variant, handlerPath } = resolveVariant(path);
      return route(handlerPath, getExports(variant), params, variant);
    } catch (err) {
      return json({ error: err.message, stack: err.stack }, 500);
    }
  },
};

function json(data, status = 200) {
  return new Response(JSON.stringify(data), { status, headers: { "Content-Type": "application/json" } });
}
