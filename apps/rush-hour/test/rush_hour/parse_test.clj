(ns rush-hour.parse-test
  (:require [clojure.test :refer [deftest is testing]]
            [rush-hour.parse :as parse]))

(deftest command-words-test
  (testing "recognised words map to their event type, case-insensitively"
    (is (= {:type :quit} (parse/parse "q")))
    (is (= {:type :quit} (parse/parse "QUIT")))
    (is (= {:type :undo} (parse/parse "u")))
    (is (= {:type :restart} (parse/parse "restart")))
    (is (= {:type :next-puzzle} (parse/parse "n")))
    (is (= {:type :hint} (parse/parse "s")))
    (is (= {:type :help} (parse/parse "?")))))

(deftest blank-input-test
  (testing "blank/whitespace-only input is a no-op, not an error"
    (is (= {:type :noop} (parse/parse "")))
    (is (= {:type :noop} (parse/parse "   ")))
    (is (= {:type :noop} (parse/parse nil)))))

(deftest move-command-test
  (testing "id + direction + explicit step count"
    (is (= {:type :move :id "X" :dir :r :steps 2} (parse/parse "Xr2"))))
  (testing "step count defaults to 1 when omitted"
    (is (= {:type :move :id "B" :dir :u :steps 1} (parse/parse "Bu"))))
  (testing "lowercase id is normalised to uppercase"
    (is (= {:type :move :id "X" :dir :l :steps 3} (parse/parse "xl3")))))

(deftest unparseable-input-test
  (testing "garbage input is a data error, not a thrown exception"
    (let [event (parse/parse "sideways")]
      (is (= :error (:type event)))
      (is (string? (:message event))))))
