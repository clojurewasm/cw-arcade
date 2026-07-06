(ns rush-hour.update-test
  (:require [clojure.test :refer [deftest is testing]]
            [rush-hour.update :as update]))

(deftest init-test
  (testing "a fresh model is playing, move-free, with no history"
    (let [model (update/init :warm-up)]
      (is (= :playing (:status model)))
      (is (zero? (:moves model)))
      (is (empty? (:history model))))))

(deftest move-event-test
  (let [model (update/init :warm-up)]
    (testing "a legal move updates vehicles, records history, counts the move"
      (let [model' (update/update model {:type :move :id "B" :dir :u :steps 1})]
        (is (= 0 (get-in model' [:vehicles "B" :row]))) ; was row 1
        (is (= 1 (:moves model')))
        (is (= 1 (count (:history model'))))))

    (testing "a move along the wrong axis is a data error, model otherwise unchanged"
      (let [model' (update/update model {:type :move :id "B" :dir :l :steps 1})]
        (is (= (:vehicles model) (:vehicles model')))
        (is (= 0 (:moves model')))
        (is (some? (:message model')))))

    (testing "an unknown vehicle id reports itself, not a crash"
      (let [model' (update/update model {:type :move :id "Z" :dir :r :steps 1})]
        (is (= "No vehicle Z." (:message model')))))))

(deftest undo-test
  (let [model (update/init :warm-up)
        moved (update/update model {:type :move :id "B" :dir :u :steps 1})]
    (testing "undo restores the prior vehicles and move count"
      (let [back (update/update moved {:type :undo})]
        (is (= (:vehicles model) (:vehicles back)))
        (is (zero? (:moves back)))))
    (testing "undoing with empty history says so instead of erroring"
      (let [nothing (update/update model {:type :undo})]
        (is (= (:vehicles model) (:vehicles nothing)))
        (is (= "Nothing to undo yet." (:message nothing)))))))

(deftest restart-test
  (let [model (update/init :warm-up)
        moved (update/update model {:type :move :id "B" :dir :u :steps 1})
        restarted (update/update moved {:type :restart})]
    (testing "restart discards all progress back to the puzzle's own layout"
      (is (= (:vehicles model) (:vehicles restarted)))
      (is (zero? (:moves restarted)))
      (is (empty? (:history restarted))))))

(deftest win-test
  (testing "clearing the exit sets :won and a congratulatory message"
    (let [model (-> (update/init :warm-up)
                     (update/update {:type :move :id "B" :dir :u :steps 1})
                     (update/update {:type :move :id "X" :dir :r :steps 6}))]
      (is (= :won (:status model)))
      (is (re-find #"Solved" (:message model))))))

(deftest hint-test
  (testing "a hint names a vehicle and a direction a player could type"
    (let [model (update/update (update/init :warm-up) {:type :hint})]
      (is (re-find #"^Hint: [A-Z] [lrud]\d+$" (:message model))))))

(deftest help-and-quit-test
  (testing "help sets the help text as the message"
    (is (= update/help-text (:message (update/update (update/init :warm-up) {:type :help})))))
  (testing "quit sets :status without touching anything else"
    (let [model (update/init :warm-up)
          quit (update/update model {:type :quit})]
      (is (= :quit (:status quit)))
      (is (= (:vehicles model) (:vehicles quit))))))

(deftest next-puzzle-test
  (testing "cycles forward, and wraps back to the first puzzle"
    (is (= :gridlock (:puzzle-id (update/update (update/init :warm-up) {:type :next-puzzle}))))
    (is (= :warm-up (:puzzle-id (update/update (update/init :gridlock) {:type :next-puzzle}))))))

(deftest error-event-test
  (testing "a parse error's message is surfaced verbatim"
    (let [model (update/update (update/init :warm-up) {:type :error :message "boom"})]
      (is (= "boom" (:message model))))))
