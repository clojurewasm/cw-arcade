(ns rush-hour.puzzles
  "Sample boards. Each is plain data — a puzzle to try is just a map to pick,
   not a class to instantiate. Solvability of each is locked in by
   rush-hour.solver-test (run against rush-hour.solver/solve), not asserted
   here.")

(def warm-up
  "One blocker, one long slide. A single-minute sanity check."
  {:width 6 :height 6 :exit-row 2 :target "X"
   :vehicles {"X" {:orientation :h :length 2 :row 2 :col 0}
              "B" {:orientation :v :length 2 :row 1 :col 3}}})

(def gridlock
  "A blocker in front of a blocker: B must move down to clear row 2, but C
   is sitting in the cell it needs to move into."
  {:width 6 :height 6 :exit-row 2 :target "X"
   :vehicles {"X" {:orientation :h :length 2 :row 2 :col 2}
              "A" {:orientation :v :length 2 :row 0 :col 2}
              "B" {:orientation :v :length 3 :row 1 :col 4}
              "C" {:orientation :h :length 2 :row 4 :col 4}}})

(def all
  {:warm-up warm-up
   :gridlock gridlock})

(def default-id :warm-up)
