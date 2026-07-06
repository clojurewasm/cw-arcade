(ns rush-hour.update-test
  (:require [clojure.test :refer [deftest is testing]]
            [rush-hour.update :as update]))

;; rush-hour.puzzles/warm-up, hand-verified in rush-hour.board-test:
;; X h/2 at row2,col0 — B v/2 at row1,col3.

(deftest init-fixed-test
  (testing "a fresh fixed-puzzle model is playing, empty, and carries its seed"
    (let [model (update/init-fixed :warm-up 42)]
      (is (= :playing (:status model)))
      (is (zero? (:moves model)))
      (is (nil? (:selection model)))
      (is (empty? (:history model)))
      (is (nil? (:difficulty model)))
      (is (= 42 (:seed model))))))

(deftest select-test
  (let [model (update/init-fixed :warm-up 1)]
    (testing "selecting a vehicle records where it started"
      (let [m (update/update model {:type :select :id "B"})]
        (is (= {:id "B" :origin-row 1 :origin-col 3} (:selection m)))))
    (testing "selecting an unknown vehicle reports itself, not a crash"
      (let [m (update/update model {:type :select :id "Z"})]
        (is (= "No vehicle Z." (:message m)))
        (is (nil? (:selection m)))))
    (testing "reselecting the vehicle already held is a true no-op"
      (let [once (update/update model {:type :select :id "B"})
            twice (update/update once {:type :select :id "B"})]
        (is (= once twice))))))

(deftest nudge-without-selection-test
  (testing "nudging with nothing selected asks you to select first"
    (let [m (update/update (update/init-fixed :warm-up 1) {:type :nudge :dir :up :steps 1})]
      (is (some? (:message m)))
      (is (nil? (:selection m))))))

(deftest nudge-wrong-axis-test
  (testing "a direction that doesn't match the vehicle's axis changes nothing at all"
    (let [model (-> (update/init-fixed :warm-up 1)
                     (update/update {:type :select :id "B"})) ; B is vertical
          m (update/update model {:type :nudge :dir :left :steps 1})]
      (is (= model m)))))

(deftest nudge-valid-test
  (testing "a valid nudge moves the vehicle but does not touch the move counter"
    (let [model (-> (update/init-fixed :warm-up 1)
                     (update/update {:type :select :id "B"}))
          m (update/update model {:type :nudge :dir :up :steps 1})]
      (is (= 0 (get-in m [:vehicles "B" :row])))
      (is (zero? (:moves m)))
      (is (= 1 (count (:history m)))))))

(deftest move-count-on-switch-test
  (testing "the counter ticks only once you switch away from a vehicle you moved"
    (let [m (-> (update/init-fixed :warm-up 1)
                (update/update {:type :select :id "B"})
                (update/update {:type :nudge :dir :up :steps 1})
                (update/update {:type :select :id "X"}))]
      (is (= 1 (:moves m)))
      (is (= {:id "X" :origin-row 2 :origin-col 0} (:selection m))))))

(deftest net-zero-move-is-free-test
  (testing "wiggling a vehicle back to exactly where it started costs nothing"
    (let [m (-> (update/init-fixed :warm-up 1)
                (update/update {:type :select :id "B"})
                (update/update {:type :nudge :dir :up :steps 1})
                (update/update {:type :nudge :dir :down :steps 1})
                (update/update {:type :select :id "X"}))]
      (is (zero? (:moves m)))
      (is (= 1 (get-in m [:vehicles "B" :row]))))))

(deftest deselect-test
  (testing "deselecting finalizes a move exactly like switching does"
    (let [m (-> (update/init-fixed :warm-up 1)
                (update/update {:type :select :id "B"})
                (update/update {:type :nudge :dir :up :steps 1})
                (update/update {:type :deselect}))]
      (is (= 1 (:moves m)))
      (is (nil? (:selection m)))))
  (testing "deselecting without having moved is free"
    (let [m (-> (update/init-fixed :warm-up 1)
                (update/update {:type :select :id "B"})
                (update/update {:type :deselect}))]
      (is (zero? (:moves m))))))

(deftest win-test
  (testing "clearing the exit sets :won, finalizes the last move, and congratulates"
    (let [m (-> (update/init-fixed :warm-up 1)
                (update/update {:type :select :id "B"})
                (update/update {:type :nudge :dir :up :steps 1})
                (update/update {:type :select :id "X"})
                (update/update {:type :nudge :dir :right :steps 6}))]
      (is (= :won (:status m)))
      (is (= 2 (:moves m)))
      (is (re-find #"Solved in 2 moves" (:message m))))))

(deftest undo-test
  (testing "undo reverts the board one nudge at a time, independent of the move counter"
    (let [moved (-> (update/init-fixed :warm-up 1)
                     (update/update {:type :select :id "B"})
                     (update/update {:type :nudge :dir :up :steps 1}))
          back (update/update moved {:type :undo})]
      (is (= 1 (get-in back [:vehicles "B" :row])))
      (is (empty? (:history back)))))
  (testing "undo past a win returns play to :playing"
    (let [won (-> (update/init-fixed :warm-up 1)
                   (update/update {:type :select :id "B"})
                   (update/update {:type :nudge :dir :up :steps 1})
                   (update/update {:type :select :id "X"})
                   (update/update {:type :nudge :dir :right :steps 6}))
          back (update/update won {:type :undo})]
      (is (= :playing (:status back)))))
  (testing "undoing with empty history says so instead of erroring"
    (let [model (update/init-fixed :warm-up 1)
          m (update/update model {:type :undo})]
      (is (= (:vehicles model) (:vehicles m)))
      (is (= "Nothing to undo yet." (:message m))))))

(deftest restart-test
  (testing "restart discards all progress back to the puzzle's own layout"
    (let [model (update/init-fixed :warm-up 1)
          moved (-> model
                    (update/update {:type :select :id "B"})
                    (update/update {:type :nudge :dir :up :steps 1}))
          restarted (update/update moved {:type :restart})]
      (is (= (:vehicles model) (:vehicles restarted)))
      (is (zero? (:moves restarted)))
      (is (nil? (:selection restarted)))
      (is (empty? (:history restarted))))))

(deftest next-puzzle-test
  (testing "from a fixed puzzle, the next puzzle graduates into generated :easy play"
    (let [m (update/update (update/init-fixed :warm-up 1) {:type :next-puzzle})]
      (is (= :easy (:difficulty m)))
      (is (= :easy (:puzzle-id m)))
      (is (:vehicles m))))
  (testing "from a generated puzzle, the next one keeps the same difficulty"
    (let [start (update/init-generated 1 :medium)
          m (update/update start {:type :next-puzzle})]
      (is (= :medium (:difficulty m)))
      (is (not= (:seed start) (:seed m))))))

(deftest hint-test
  (testing "a hint names the vehicle and the single letter that advances the solution"
    (let [m (update/update (update/init-fixed :warm-up 1) {:type :hint})]
      (is (re-matches #"Hint: [A-Z], then [hjkl]" (:message m))))))

(deftest help-and-quit-test
  (testing "help sets the help text as the message"
    (is (= update/help-text (:message (update/update (update/init-fixed :warm-up 1) {:type :help})))))
  (testing "quit sets :status without touching anything else"
    (let [model (update/init-fixed :warm-up 1)
          quit (update/update model {:type :quit})]
      (is (= :quit (:status quit)))
      (is (= (:vehicles model) (:vehicles quit))))))

(deftest error-event-test
  (testing "a parse error's message is surfaced verbatim"
    (let [m (update/update (update/init-fixed :warm-up 1) {:type :error :message "boom"})]
      (is (= "boom" (:message m))))))
