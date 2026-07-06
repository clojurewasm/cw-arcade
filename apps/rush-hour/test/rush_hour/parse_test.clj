(ns rush-hour.parse-test
  (:require [clojure.test :refer [deftest is testing]]
            [rush-hour.parse :as parse]))

(deftest command-words-test
  (testing "recognised words map to their event type, case-insensitively"
    (is (= {:type :quit} (parse/parse "q")))
    (is (= {:type :quit} (parse/parse "Q")))
    (is (= {:type :undo} (parse/parse "z")))
    (is (= {:type :restart} (parse/parse "r")))
    (is (= {:type :next-puzzle} (parse/parse "p")))
    (is (= {:type :hint} (parse/parse "s")))
    (is (= {:type :help} (parse/parse "?")))
    (is (= {:type :deselect} (parse/parse "0")))))

(deftest blank-input-test
  (testing "blank/whitespace-only input is a no-op, not an error"
    (is (= {:type :noop} (parse/parse "")))
    (is (= {:type :noop} (parse/parse "   ")))
    (is (= {:type :noop} (parse/parse nil)))))

(deftest select-test
  (testing "any bare letter selects that vehicle, uppercased"
    (is (= {:type :select :id "A"} (parse/parse "a")))
    (is (= {:type :select :id "A"} (parse/parse "A")))
    (is (= {:type :select :id "X"} (parse/parse "x")))))

(deftest nudge-test
  (testing "h/j/k/l nudge one cell by default"
    (is (= {:type :nudge :dir :left :steps 1} (parse/parse "h")))
    (is (= {:type :nudge :dir :down :steps 1} (parse/parse "j")))
    (is (= {:type :nudge :dir :up :steps 1} (parse/parse "k")))
    (is (= {:type :nudge :dir :right :steps 1} (parse/parse "l"))))
  (testing "a leading count works like vim's, e.g. 5j"
    (is (= {:type :nudge :dir :down :steps 5} (parse/parse "5j"))))
  (testing "uppercase direction letters work too"
    (is (= {:type :nudge :dir :right :steps 3} (parse/parse "3L")))))

(deftest unparseable-input-test
  (testing "garbage input is a data error, not a thrown exception"
    (let [event (parse/parse "sideways")]
      (is (= :error (:type event)))
      (is (string? (:message event))))))
