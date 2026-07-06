(ns rush-hour.core
  "The imperative shell (Bernhardt's \"Boundaries\") — the only namespace in
   this app allowed to touch a terminal. It is deliberately as thin as
   possible: read a line, rush-hour.parse it into an event, fold the event
   through rush-hour.update, rush-hour.view render the result, print it,
   repeat until :quit. Every decision about what a move means or how the
   board looks lives elsewhere, in pure code that doesn't know a terminal
   exists.

   Input is line-based (read-line), not raw single-keystroke — deliberately:
   raw terminal mode is OS-level (termios/stty) and isn't something plain
   Clojure code can reach portably across a real JVM, Babashka, and cljw
   without shelling out. A full-redraw-per-line TUI needs only `read-line`
   / `print` / `flush`, so it runs identically on all three."
  (:require [clojure.string :as str]
            [rush-hour.parse :as parse]
            [rush-hour.puzzles :as puzzles]
            [rush-hour.update :as update]
            [rush-hour.view :as view]))

(defn- prompt! []
  (print "> ")
  (flush))

(defn run
  "The main loop: render, prompt, read, update, repeat. Returns the final
   model (handy for scripted/tested runs; the terminal side effects along
   the way are what a human sitting at a TUI actually sees)."
  [initial-model]
  (loop [model initial-model]
    (print (view/render model))
    (flush)
    (if (= (:status model) :quit)
      model
      (do
        (prompt!)
        (if-let [line (read-line)]
          (recur (update/update model (parse/parse line)))
          model)))))

(defn -main
  [& args]
  (let [puzzle-id (if-let [arg (first args)]
                    (keyword arg)
                    puzzles/default-id)]
    (if (contains? puzzles/all puzzle-id)
      (run (update/init puzzle-id))
      (do
        (println "Unknown puzzle:" (name puzzle-id))
        (println "Available:" (str/join ", " (map name (keys puzzles/all))))))))
