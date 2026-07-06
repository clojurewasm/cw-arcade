(ns rush-hour.board-test
  (:require [clojure.test :refer [deftest is testing]]
            [rush-hour.board :as board]))

(def puzzle {:width 6 :height 6 :exit-row 2 :target "X"})

(def vehicles
  {"X" {:orientation :h :length 2 :row 2 :col 0}
   "B" {:orientation :v :length 2 :row 1 :col 3}})

(deftest cells-test
  (testing "horizontal vehicle occupies consecutive columns"
    (is (= [[2 0] [2 1]] (board/cells (get vehicles "X")))))
  (testing "vertical vehicle occupies consecutive rows"
    (is (= [[1 3] [2 3]] (board/cells (get vehicles "B"))))))

(deftest occupied-test
  (testing "every vehicle's cells are indexed by id"
    (is (= {[2 0] "X" [2 1] "X" [1 3] "B" [2 3] "B"}
           (board/occupied vehicles))))
  (testing "ignore-id omits that vehicle's cells"
    (is (= {[1 3] "B" [2 3] "B"} (board/occupied vehicles "X")))))

(deftest move-test
  (testing "a clear path succeeds and returns the moved vehicle map"
    (let [{:keys [ok? vehicles]} (board/move puzzle vehicles "B" -1)]
      (is ok?)
      (is (= {:orientation :v :length 2 :row 0 :col 3} (get vehicles "B")))))

  (testing "a path through another vehicle's cells is blocked"
    (let [result (board/move puzzle vehicles "X" 6)]
      (is (false? (:ok? result)))
      (is (= :blocked (:reason result)))))

  (testing "leaving the grid anywhere but the exit gap is out of bounds"
    (let [result (board/move puzzle vehicles "B" -2)]
      (is (false? (:ok? result)))
      (is (= :out-of-bounds (:reason result)))))

  (testing "an unknown vehicle id is its own distinct error"
    (is (= :no-such-vehicle (:reason (board/move puzzle vehicles "Z" 1)))))

  (testing "a zero delta is rejected as a no-op, not silently accepted"
    (is (= :no-op (:reason (board/move puzzle vehicles "X" 0))))))

(deftest win-test
  (let [cleared-b (:vehicles (board/move puzzle vehicles "B" -1))]
    (testing "reaching the wall is not yet a win — the vehicle must clear it"
      (let [{:keys [ok? vehicles]} (board/move puzzle cleared-b "X" 5)]
        (is ok?)
        (is (not (board/win? puzzle vehicles)))))
    (testing "every cell past the wall is a win"
      (let [{:keys [ok? vehicles]} (board/move puzzle cleared-b "X" 6)]
        (is ok?)
        (is (board/win? puzzle vehicles))))))

(deftest legal-moves-test
  (testing "every open single-cell step appears, blocked/out-of-bounds ones don't"
    (let [moves (set (board/legal-moves puzzle vehicles))]
      ;; X can only step right (left runs off the grid at the non-exit side)
      (is (contains? moves {:id "X" :delta 1}))
      (is (not (contains? moves {:id "X" :delta -1})))
      ;; B is boxed in by nothing yet, so both single-cell steps are open
      (is (contains? moves {:id "B" :delta -1}))
      (is (contains? moves {:id "B" :delta 1})))))
