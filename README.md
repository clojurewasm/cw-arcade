# cw-arcade

A flat arcade of small Clojure demos and applications. Each one is its own
cabinet, standing side by side under [`apps/`](./apps) — no per-demo
repositories to manage, no hierarchy to navigate, just pick one and play.

Every cabinet aims to run on **real Clojure, [Babashka](https://babashka.org/),
and [ClojureWasm](https://github.com/clojurewasm/ClojureWasm) (`cljw`) alike** —
runtime-agnostic by default. A cabinet is free to lean on `cljw`-specific
features (like its WebAssembly FFI) when that's the point of the demo, but
that's the exception, not the rule.

## Cabinets

| App | What it is |
|---|---|
| [`rush-hour`](./apps/rush-hour) | The sliding-block puzzle, as a terminal app — an Elm-Architecture-shaped TUI over a pure, data-oriented game core. |

## Structure

```
cw-arcade/
└── apps/
    └── <name>/          # fully self-contained: its own deps.edn, its own tests
        ├── deps.edn
        ├── bb.edn
        ├── src/
        └── test/
```

Each cabinet owns its own `deps.edn`/`bb.edn` and dependencies. There's no
shared build or root-level dependency file — if a cabinet ever needs to
become its own repository, it's already a clean `git subtree split` away.

## Adding a cabinet

1. `apps/<name>/` with its own `deps.edn` (and `bb.edn` if it should run on
   Babashka too).
2. Keep game/app logic as pure data transformations, separate from whatever
   touches a terminal, a clock, or a file — see `rush-hour`'s README for the
   pattern (functional core / imperative shell, Elm Architecture) and why
   it's worth following even outside a GUI context.
3. Add a row to the table above.

## License

MIT — see [`LICENSE`](./LICENSE).
