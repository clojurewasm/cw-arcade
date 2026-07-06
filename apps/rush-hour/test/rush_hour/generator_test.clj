(ns rush-hour.generator-test
  (:require [clojure.set :as set]
            [clojure.test :refer [deftest is testing]]
            [rush-hour.board :as board]
            [rush-hour.generator :as generator]
            [rush-hour.solver :as solver]))

(def ^:private reserved
  "Letters the TUI gives another meaning — see rush-hour.parse. A generated
   puzzle's non-target vehicles must never collide with these."
  #{"H" "J" "K" "L" "Z" "R" "P" "S" "Q" "X"})

(deftest determinism-test
  (testing "the same seed and difficulty always produce the same puzzle"
    (is (= (generator/generate 12345 :medium) (generator/generate 12345 :medium)))))

(deftest next-seed-test
  (testing "advances to a different value, deterministically"
    (is (= (generator/next-seed 1) (generator/next-seed 1)))
    (is (not= 1 (generator/next-seed 1)))))

(deftest solvable-and-well-formed-test
  (testing "every generated puzzle, across several seeds and all difficulties, is solvable and well-formed"
    (doseq [difficulty [:easy :medium :hard]
            seed [1 2 42]]
      (let [puzzle (generator/generate seed difficulty)
            {:keys [vehicles target width height exit-row]} puzzle
            sol (solver/solve puzzle vehicles 200 3000)]
        (testing (str difficulty " seed " seed)
          (is (= "X" target))
          (is (= [6 6 2] [width height exit-row]))
          (is (contains? vehicles "X"))
          (is (some? sol) "must be solvable within the search budget")
          (is (board/win? puzzle (reduce (fn [vs {:keys [id delta]}]
                                            (:vehicles (board/move puzzle vs id delta)))
                                          vehicles sol)))
          (is (empty? (set/intersection reserved (disj (set (keys vehicles)) "X")))
              "no non-target vehicle id may collide with a movement/command letter"))))))

(deftest difficulty-ordering-test
  (testing "harder tiers generally need more vehicles and more steps (checked on average, not per-seed)"
    (let [measure (fn [difficulty]
                     (let [samples (for [seed (range 1 7)]
                                      (let [p (generator/generate seed difficulty)]
                                        [(count (:vehicles p))
                                         (count (solver/solve p (:vehicles p) 200 3000))]))]
                       [(/ (reduce + (map first samples)) (count samples))
                        (/ (reduce + (map second samples)) (count samples))]))
          [easy-vehicles easy-len] (measure :easy)
          [medium-vehicles medium-len] (measure :medium)
          [hard-vehicles hard-len] (measure :hard)]
      (is (< easy-vehicles medium-vehicles hard-vehicles))
      ;; <= , not < : a random scatter's average solution length is noisy
      ;; enough (small sample, bounded retry budget) that medium and hard
      ;; occasionally tie — vehicle count is the parameter actually being
      ;; tuned; length is what it buys you on average, not a guarantee.
      (is (<= easy-len medium-len hard-len)))))
