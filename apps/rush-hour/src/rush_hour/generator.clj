(ns rush-hour.generator
  "The 作問エンジン: a pure, seeded puzzle generator. `generate` is
   `(seed, difficulty) -> puzzle` — deterministic, so the same seed always
   gives the same board, and no hidden mutable RNG object leaks into the
   rest of the app. Randomness is data (a seed you thread through), not a
   stateful `java.util.Random` you mutate; that's what lets rush-hour.core
   (the imperative shell) be the only place a *fresh* seed ever gets
   minted, while the generator itself stays as pure as rush-hour.board.

   Approach: generate-and-test. Place the target car, then scatter cars
   (length 2) and trucks (length 3) at random non-overlapping spots, then
   ask rush-hour.solver whether the result is solvable and how hard —
   retrying with a new derived seed until a candidate lands in the
   difficulty's target solution-length window, or a retry budget runs out."
  (:require [rush-hour.board :as board]
            [rush-hour.solver :as solver]))

(def width 6)
(def height 6)
(def exit-row 2)
(def target "X")

;; The 16 letters left over once the alphabet is partitioned for the TUI:
;; h/j/k/l move the selected vehicle (vim-style), z/r/p/s/q are commands,
;; x is always the target — every other letter is fair game for a vehicle
;; id. Kept single-character on purpose: a two-character id (e.g. a bare
;; number) would make that vehicle's cells one column wider than every
;; other cell in the grid and break the board's alignment. 16 slots is
;; already one more than a real set's 15 non-target pieces (11 cars + 4
;; trucks) needs — see rush-hour.view for where that 11/4 split becomes a
;; colour-palette limit.
(def ^:private ids (mapv str "ABCDEFGIMNOTUVWY"))

(def difficulties
  "Per-tier target vehicle count (excluding the target car) and acceptable
   solution-length window (in rush-hour.solver's single-cell-step units).
   Tuned empirically against what random placement on a 6x6 board actually
   produces — see generator-test for the measured distribution. Deliberately
   modest at the top end: a random scatter rarely stumbles into the kind of
   deliberately-interlocked deadlock a hand-designed \"expert\" Rush Hour
   card has, and chasing a much longer solution here mostly just means
   burning the retry budget on rejections rather than getting meaningfully
   harder boards — see the perf note on `probe-max-states` below."
  {:easy   {:extra-vehicles [3 5] :solution-length [3 9]}
   :medium {:extra-vehicles [5 7] :solution-length [7 14]}
   :hard   {:extra-vehicles [7 9] :solution-length [10 20]}})

;; --- a tiny portable PRNG -------------------------------------------------
;; MINSTD (Park-Miller): x' = x * 48271 mod (2^31 - 1). Plain fixnum
;; arithmetic, no clojure.lang.* or java.util.Random reference — every
;; runtime this app targets (clj/bb/cljw) just needs `*`/`mod` to agree.

(def ^:private modulus 2147483647)

(defn next-seed
  "One PRNG step — public so callers (rush-hour.update) can derive the seed
   for a *next* puzzle without duplicating the LCG, while still minting no
   randomness of their own."
  [seed]
  (mod (* seed 48271) modulus))

(defn- rand-below
  "[0, n) and the next seed, from one PRNG step."
  [seed n]
  (let [seed' (next-seed seed)]
    [(mod seed' n) seed']))

(defn- rand-nth-seeded
  [seed coll]
  (let [[i seed'] (rand-below seed (count coll))]
    [(nth coll i) seed']))

;; --- placement -------------------------------------------------------------

(defn- fits?
  [vehicle occupied]
  (every? (fn [cell]
            (and (<= 0 (first cell)) (< (first cell) height)
                 (<= 0 (second cell)) (< (second cell) width)
                 (not (contains? occupied cell))))
          (board/cells vehicle)))

(defn- place-target
  [seed]
  (let [[col seed'] (rand-below seed (inc (- width 2)))]
    [{:orientation :h :length 2 :row exit-row :col col} seed']))

(defn- place-one
  "Try up to `attempts` random spots for a vehicle of `length`; nil if none
   fit. Skipping a vehicle that won't fit (rather than backtracking the
   whole board) is the deliberate trade-off that keeps generation O(vehicles
   x attempts) instead of exponential — see the namespace docstring."
  [seed length occupied attempts]
  (loop [seed seed n attempts]
    (if (zero? n)
      [nil seed]
      (let [[orientation seed1] (rand-nth-seeded seed [:h :v])
            max-row (if (= orientation :v) (- height length) (dec height))
            max-col (if (= orientation :h) (- width length) (dec width))
            [row seed2] (rand-below seed1 (inc max-row))
            [col seed3] (rand-below seed2 (inc max-col))
            candidate {:orientation orientation :length length :row row :col col}]
        (if (fits? candidate occupied)
          [candidate seed3]
          (recur seed3 (dec n)))))))

(defn- scatter
  "Place `n` vehicles of `length`, skipping any that don't fit within the
   attempt budget. Returns [vehicles' seed']."
  [seed vehicles ids-left length n]
  (loop [seed seed vehicles vehicles ids ids-left n n]
    (if (or (zero? n) (empty? ids))
      [vehicles seed (rest ids)]
      (let [occupied (board/occupied vehicles)
            [placed seed'] (place-one seed length occupied 50)]
        (if placed
          (recur seed' (assoc vehicles (first ids) placed) (rest ids) (dec n))
          (recur seed' vehicles (rest ids) (dec n)))))))

(defn- candidate
  "One random board attempt: the target car plus up to `truck-count` +
   `car-count` scattered vehicles. Not guaranteed solvable — the caller
   checks that."
  [seed truck-count car-count]
  (let [[target-v seed1] (place-target seed)
        vehicles {target target-v}
        [vehicles seed2 ids-left] (scatter seed1 vehicles ids 3 truck-count)
        [vehicles seed3 _] (scatter seed2 vehicles ids-left 2 car-count)]
    [{:width width :height height :exit-row exit-row :target target
      :vehicles vehicles}
     seed3]))

(defn generate
  "(seed, difficulty) -> a solvable puzzle whose solution length falls
   inside that difficulty's window when one is found within the retry
   budget, else the best (closest-to-window) candidate seen. Deterministic:
   same seed + difficulty always yields the same puzzle."
  ([seed difficulty] (generate seed difficulty 80))
  ([seed difficulty max-attempts]
   (let [{:keys [extra-vehicles solution-length]} (get difficulties difficulty)
         [lo-len hi-len] solution-length
         ;; Anything past the window is a reject either way, so the probe
         ;; doesn't need to search past it (+ a margin, to still recognise
         ;; "just barely too hard" for the closest-fit fallback).
         probe-depth (+ hi-len 15)
         ;; A dense random scatter occasionally produces a reachable-state
         ;; graph in the hundreds of thousands (measured one real candidate
         ;; at ~16s before this cap) — generation needs to reject that kind
         ;; of candidate cheaply, not resolve it exhaustively. Tuned against
         ;; Babashka specifically (no JIT warmup, so noticeably slower per
         ;; state than the JVM or cljw here) rather than just the JVM —
         ;; see generator-test's measured timing.
         probe-max-states 1000
         distance (fn [len] (cond (< len lo-len) (- lo-len len)
                                   (> len hi-len) (- len hi-len)
                                   :else 0))]
     (loop [seed seed attempt 0 best nil best-distance nil]
       (let [[truck-count seed1] (rand-below seed 5) ; 0..4 trucks
             extra (+ (first extra-vehicles)
                      (mod seed1 (inc (- (second extra-vehicles) (first extra-vehicles)))))
             truck-count (min truck-count extra)
             car-count (- extra truck-count)
             [puzzle seed2] (candidate seed1 truck-count car-count)
             sol (solver/solve puzzle (:vehicles puzzle) probe-depth probe-max-states)
             len (some-> sol count)
             d (when len (distance len))]
         (cond
           (= d 0) puzzle

           (>= attempt max-attempts)
           (or best puzzle)

           (and len (or (nil? best-distance) (< d best-distance)))
           (recur seed2 (inc attempt) puzzle d)

           :else
           (recur seed2 (inc attempt) best best-distance)))))))
