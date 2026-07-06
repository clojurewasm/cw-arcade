# rush-hour

The sliding-block puzzle, as a terminal app. Slide cars and trucks around a
6×6 grid to clear a path for the red car to the exit.

```
+------------+
|. . A . . . |
|. . A . B . |
|. . X X B . >
|. . . . B . |
|. . . . C C |
|. . . . . . |
+------------+
Puzzle: gridlock   Moves: 0
> Commands: <vehicle><dir><n>  e.g. Xr2 (X right 2), Bu1 (B up 1)
          l/r/u/d = left/right/up/down, n defaults to 1
          u undo · r restart · n next puzzle · s hint · q quit
```

## Run it

```sh
# Real Clojure
clojure -M:run [warm-up|gridlock]

# Babashka
bb -m rush-hour.core [warm-up|gridlock]

# ClojureWasm (cljw) — no JVM
cljw -m rush-hour.core [warm-up|gridlock]
```

Same source, same three runtimes, no conditionals in the code for which one
you're on.

## Test it

```sh
clojure -M:test                       # real Clojure
bb -cp src:test -m rush-hour.test-runner    # Babashka
cljw -A:test -m rush-hour.test-runner       # cljw
```

68 assertions over the pure game logic — board rules, input parsing, the
solver, and full game-state transitions — no terminal required.

## Architecture

This app is a straight port of **[The Elm
Architecture](https://guide.elm-lang.org/architecture/)** (Model / Update /
View) onto a terminal, in the same spirit as Go's
[Bubble Tea](https://github.com/charmbracelet/bubbletea) and Rust's
[Ratatui](https://ratatui.rs/concepts/application-patterns/the-elm-architecture/)
— both of which name and adopt the same pattern for TUIs. It's also just
Gary Bernhardt's **["functional core, imperative
shell"](https://www.destroyallsoftware.com/talks/boundaries)**: nearly every
namespace here is pure `data -> data`, and exactly one (`core`) is allowed to
touch a terminal. ClojureScript's [re-frame](https://github.com/day8/re-frame)
converges on the identical shape (event → pure handler → single `app-db` →
view) inside the Clojure world specifically — which is the point: this isn't
an idiom invented for this game, it's the same four pieces — *one state
value, a pure transition function, a pure render function, a thin I/O loop* —
that shows up independently everywhere clean UI architecture gets discussed,
in and out of Lisp.

| Namespace | Role | Touches I/O? |
|---|---|---|
| `rush-hour.board` | Vehicle/board data, moves, collisions, win check | No |
| `rush-hour.puzzles` | Sample boards, as plain data | No |
| `rush-hour.solver` | BFS shortest-solution search | No |
| `rush-hour.parse` | Input line → event map | No |
| `rush-hour.update` | `(model, event) -> model'` — the Update | No |
| `rush-hour.view` | `model -> string` — the View | No |
| `rush-hour.core` | The loop: render, prompt, read, update, repeat | **Yes — only this one** |

Why this is worth it here specifically: `rush-hour.solver` explores the
entire game's state graph with a plain `#{}` as its visited-set, because a
game state is *just a map* — two states are `=` exactly when every vehicle
occupies the same cells. There's no identity to manage, no `hashCode`/`equals`
to write, no risk of aliasing a board you meant to snapshot. An
object-oriented board of mutable pieces would need all of that machinery
by hand before search is even possible; here it's the default behaviour of
`=` on a map.

Every pure fn in this app is exercised directly by `clojure.test` — no
terminal, no mocking a screen, no driving a fake keyboard. That's the other
half of the payoff: the *shell* (`rush-hour.core`) is the only place that
could possibly need a human or a script to test, and it's about fifteen
lines.

## Why line-based input, not raw keystrokes

A "real" TUI usually reads one keypress at a time, with no Enter required.
That needs the terminal in *raw mode* (`termios`/`stty`), which is an OS-level
concept — not something portable Clojure code can reach on the JVM, Babashka,
and cljw alike without shelling out to `stty` per-platform. Since running on
all three runtimes unmodified was a goal here, this app instead does a full
screen redraw after every *line* of input (`read-line` / `print` / `flush` —
plain `clojure.core`, nothing host-specific). It reads as choppier than a
"real" arcade cabinet, but the game and its rules don't know or care — the
input adapter is the one piece of the whole app a future raw-mode version
would need to replace.

## Puzzles

- `warm-up` — one blocker, one long slide.
- `gridlock` — a blocker in front of a blocker; the solver confirms it's
  solvable (see `rush-hour.solver-test`), but it takes real untangling.

`n` cycles between them mid-game; pass either name as a CLI arg to start on
one directly.

## Found along the way

Building this surfaced a real bug in `cljw`: `read-line` returned `nil` for
any non-interactive stdin (pipe or file redirect) instead of the actual
input line, which blocked scripted/automated playthroughs specifically (the
pure-logic test suite was unaffected and passed on `cljw` from the start).
Fixed upstream in ClojureWasm (`*in*`'s root was an unset stdin reader) —
this app's automated playthrough is what caught it.
