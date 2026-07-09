# rush-hour

The sliding-block puzzle, as a terminal app. Slide cars and trucks around a
6×6 grid to clear a path for the red car to the exit. A puzzle generator
keeps producing fresh boards — clear one and the next appears automatically.

```
+------------+
|. . A . . . |
|. . A . B . |
|. . X X B . >
|. . . . B . |
|. . . . C C |
|. . . . . . |
+------------+
Puzzle: gridlock   Moves: 0   Selected: -
> Select a vehicle by its letter (x is the escaping car).
  h/j/k/l  nudge it left/down/up/right — a leading count works, e.g. 5j
  0 deselect (no penalty) · z undo · r restart · p new puzzle
  s hint · q quit
A move only counts once you switch away from a vehicle you actually moved.
```

## Run it

```sh
# Real Clojure
clojure -M:run [easy|medium|hard|warm-up|gridlock]

# Babashka
bb -m rush-hour.core [easy|medium|hard|warm-up|gridlock]

# ClojureWasm (cljw) — no JVM
cljw -m rush-hour.core [easy|medium|hard|warm-up|gridlock]
```

> **Note on cljw speed:** the solver and generator work on cljw (the crash
> this note used to describe was root-caused upstream — see "Found along
> the way" — and is fixed on ClojureWasm HEAD), but the generator's
> generate-and-test loop runs on a pure interpreter there, so a fresh
> `medium`/`hard` board can take noticeably longer to generate than on a
> warmed JVM. `warm-up`/`gridlock` and hints on small boards are quick.

No argument starts a fresh `easy` puzzle from the generator. `warm-up` and
`gridlock` are two hand-built puzzles kept as a fixed reference (and as a
regression anchor — see `rush-hour.board-test`/`rush-hour.solver-test`).
Same source, same three runtimes, no conditionals in the code for which one
you're on.

## Controls

Select a vehicle by typing its letter, then nudge it with `h`/`j`/`k`/`l`
(left/down/up/right, vim-style) — a leading count works the same way vim's
does, e.g. `5j` nudges 5 cells down in one line. Pressing a direction the
vehicle can't move in (wrong axis) does nothing at all, silently — there's
nothing useful to say about it. `0` deselects; `z` undoes one nudge; `r`
restarts the current puzzle; `p` skips to a new one; `s` asks the solver for
a hint; `q` quits.

**The move counter only ticks when you leave a vehicle you actually moved** —
switching to a different vehicle, deselecting, or winning. Nudging back and
forth and ending up exactly where you picked a vehicle up costs nothing, no
matter how many nudges that took; that comparison is always against the
vehicle's live position, so it holds even across `z` (undo).

Once a puzzle is solved, any key except `q` loads a fresh one at the same
difficulty (or `easy`, the first time you graduate off `warm-up`/`gridlock`).

## Run it — test it

```sh
clojure -M:test                       # real Clojure
bb -cp src:test -m rush-hour.test-runner    # Babashka
cljw -A:test -m rush-hour.test-runner       # cljw
```

Assertions over the pure game logic — board rules, the puzzle generator,
input parsing, the solver, and full game-state transitions — no terminal
required.

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
touch a terminal — or a clock, or an RNG (see below). ClojureScript's
[re-frame](https://github.com/day8/re-frame) converges on the identical shape
(event → pure handler → single `app-db` → view) inside the Clojure world
specifically — which is the point: this isn't an idiom invented for this
game, it's the same four pieces — *one state value, a pure transition
function, a pure render function, a thin I/O loop* — that shows up
independently everywhere clean UI architecture gets discussed, in and out of
Lisp.

| Namespace | Role | Touches I/O? |
|---|---|---|
| `rush-hour.board` | Vehicle/board data, moves, collisions, win check | No |
| `rush-hour.puzzles` | Hand-built sample boards, as plain data | No |
| `rush-hour.solver` | BFS shortest-solution search | No |
| `rush-hour.generator` | Seeded puzzle generator (the "作問エンジン") | No |
| `rush-hour.parse` | Input line → event map | No |
| `rush-hour.update` | `(model, event) -> model'` — the Update | No |
| `rush-hour.view` | `model -> string` — the View | No |
| `rush-hour.core` | The loop: render, prompt, read, update, repeat; the only place a seed ever gets minted | **Yes — only this one** |

Why this is worth it here specifically: `rush-hour.solver` explores the
entire game's state graph with a plain `#{}` as its visited-set, because a
game state is *just a map* — two states are `=` exactly when every vehicle
occupies the same cells. There's no identity to manage, no `hashCode`/`equals`
to write, no risk of aliasing a board you meant to snapshot. An
object-oriented board of mutable pieces would need all of that machinery
by hand before search is even possible; here it's the default behaviour of
`=` on a map. `rush-hour.generator` leans on the same fact from the other
direction — every candidate board it scatters is checked for solvability by
just calling the same `solver/solve`, no separate "is this generator-safe"
logic needed.

Randomness gets the same treatment as the terminal: `rush-hour.generator` is
`(seed, difficulty) -> puzzle`, deterministic, using a tiny hand-rolled LCG
instead of a mutable `java.util.Random` — a seed is a plain number you thread
through, not a stateful object you mutate. `rush-hour.core` is the only place
that ever calls `rand-int` to mint a *fresh* seed (once, at startup); every
puzzle after that — including the automatic next-puzzle-on-win — is a pure
function of a seed the model already carries forward.

Every pure fn in this app is exercised directly by `clojure.test` — no
terminal, no mocking a screen, no driving a fake keyboard, no seeding a real
RNG. That's the other half of the payoff: the *shell* (`rush-hour.core`) is
the only place that could possibly need a human or a script to test, and
it's a few dozen lines.

## Why line-based input, not raw keystrokes

A "real" TUI usually reads one keypress at a time, with no Enter required.
That needs the terminal in *raw mode* (`termios`/`stty`), which is an OS-level
concept — not something portable Clojure code can reach on the JVM, Babashka,
and cljw alike without shelling out to `stty` per-platform (and then
restoring the terminal on exit, including on a signal — its own portability
problem). Since running on all three runtimes unmodified was a goal here,
this app instead does a full screen redraw after every *line* of input
(`read-line` / `print` / `flush` — plain `clojure.core`, nothing
host-specific). The `h`/`j`/`k`/`l` + leading-count scheme is chosen to make
that as painless as it can be — vim users already reach for it without
thinking — but pressing Enter after every nudge is the one real cost of
staying portable; the input adapter (`rush-hour.core`, `rush-hour.parse`) is
the one piece of the whole app a future raw-mode version would need to
replace.

## Vehicle ids and colour

Vehicle ids are single letters, chosen to never collide with a control
scheme character: `h j k l` (movement), `z r p s q` (commands), and `x`
(always the target) are reserved, leaving 16 letters —
`a b c d e f g i m n o t u v w y` — for everything else. Colour follows a
real Rush Hour set: the target is always red, trucks (length 3) draw from a
4-colour palette and cars (length 2) from an 11-colour one, using 256-colour
ANSI (`\e[38;5;Nm`) since the basic 16-colour set can't tell that many
vehicles apart at a glance. The selected vehicle renders bold. Colour and
weight are decoration only — `rush-hour.board` has no notion of either.

## Puzzles

- `warm-up` / `gridlock` — two hand-built puzzles, kept as a fixed,
  solver-verified reference.
- `easy` / `medium` / `hard` — `rush-hour.generator` scatters cars and trucks
  at random, keeps only boards the solver confirms are solvable, and retries
  until the solution length lands in the difficulty's target window (or a
  retry budget runs out, in which case it keeps the closest attempt —
  see `rush-hour.generator-test` for the measured distribution).

`p` (or any key but `q` once a puzzle is solved) always gets you a new one;
starting from `warm-up`/`gridlock` and asking for the next puzzle graduates
you into generated `easy` play.

**Generation speed, and why `hard` isn't "just more vehicles":** a plain BFS
over a dense board's reachable-state graph gets expensive fast — one early
13-vehicle candidate took over 15 seconds to resolve before this app capped
the search (`rush-hour.solver`'s `max-states`, and `rush-hour.generator`'s
own tighter probe budget during generate-and-test). `easy`/`medium` generate
in well under a second on real Clojure; `hard` is normally sub-second there
too, but can take a few seconds on Babashka (no JIT warmup) and noticeably
longer on cljw (a pure interpreter today — this CPU-bound search is the
worst case for it; unlucky `hard` seeds can take minutes). That's also why the difficulty tiers stay
modest at the top end rather than chasing a real "expert" card's forced
30+-move solutions: a random scatter rarely stumbles into that kind of
deliberately-interlocked deadlock, so pushing the target window higher
mostly burns the retry budget on rejections instead of producing meaningfully
harder boards.

## Found along the way

Building this surfaced two real bugs in `cljw`:

- `read-line` returned `nil` for any non-interactive stdin (pipe or file
  redirect) instead of the actual input line, which blocked
  scripted/automated playthroughs specifically. Fixed upstream (`*in*`'s
  root was an unset stdin reader) — this app's automated playthrough is
  what caught it.
- The solver's BFS — a `loop`/`recur`-accumulated two-list queue of
  `[state path]` pairs — hit a garbage-collector use-after-free: under
  allocation pressure a queue element would decay into a bare number or
  string mid-search. Reported upstream with a dependency-free repro; the
  root cause turned out to be three tokens wide (`(first (rest (range 2)))`
  could read freed memory when a collection landed inside the chunked-seq
  advance). Fixed upstream in ClojureWasm; this suite is one of its
  regression guards now.
