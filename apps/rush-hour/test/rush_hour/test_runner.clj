(ns rush-hour.test-runner
  "A hand-rolled runner instead of a `:mvn/version` test-runner dependency —
   keeps the app at zero external deps, which matters here specifically
   because cljw has no Maven artifact fetcher (deps.edn :paths/:git coords
   resolve; :mvn/version coords are skipped). Plain `clojure.test`, invoked
   the same way under clj, bb, and cljw."
  (:require [clojure.test :as t]
            [rush-hour.board-test]
            [rush-hour.generator-test]
            [rush-hour.parse-test]
            [rush-hour.solver-test]
            [rush-hour.update-test]))

(defn -main [& _]
  (t/run-tests 'rush-hour.board-test
                'rush-hour.generator-test
                'rush-hour.parse-test
                'rush-hour.solver-test
                'rush-hour.update-test))
