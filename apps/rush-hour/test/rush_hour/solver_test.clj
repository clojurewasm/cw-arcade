(ns rush-hour.solver-test
  (:require [clojure.test :refer [deftest is testing]]
            [rush-hour.board :as board]
            [rush-hour.puzzles :as puzzles]
            [rush-hour.solver :as solver]))

(defn- replay
  "Fold a solver path back through board/move, for asserting it actually
   reaches a win — the solver's own answer, checked against the same rules
   a human player is bound by."
  [puzzle vehicles path]
  (reduce (fn [vs {:keys [id delta]}] (:vehicles (board/move puzzle vs id delta)))
          vehicles
          path))

(deftest warm-up-is-solved-test
  (let [{:keys [vehicles] :as puzzle} puzzles/warm-up
        path (solver/solve puzzle vehicles)]
    (testing "a solution exists"
      (is (some? path)))
    (testing "replaying it against the board rules actually wins"
      (is (board/win? puzzle (replay puzzle vehicles path))))
    (testing "it's the known-shortest single-cell-step count (regression)"
      (is (= 7 (count path))))))

(deftest gridlock-is-solvable-test
  (let [{:keys [vehicles] :as puzzle} puzzles/gridlock
        path (solver/solve puzzle vehicles)]
    (testing "a solution exists and actually wins when replayed"
      (is (some? path))
      (is (board/win? puzzle (replay puzzle vehicles path))))))

(deftest hint-test
  (let [{:keys [vehicles] :as puzzle} puzzles/warm-up]
    (testing "hint is just the solver's first step"
      (is (= (first (solver/solve puzzle vehicles)) (solver/hint puzzle vehicles))))
    (testing "no hint once already won"
      (let [won (assoc-in vehicles ["X" :col] 6)]
        (is (nil? (solver/hint puzzle won)))))))
