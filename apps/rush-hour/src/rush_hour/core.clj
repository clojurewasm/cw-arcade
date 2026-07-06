(ns rush-hour.core
  "The imperative shell (Bernhardt's \"Boundaries\") — the only namespace in
   this app allowed to touch a terminal, and (with rush-hour.update) the
   only one allowed to know randomness exists at all: `-main` mints one
   fresh seed via `rand-int`, then every puzzle after that is a pure,
   deterministic function of a seed the model already carries.

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

(defn- won-screen-event
  "At the solved screen, any input advances to a new puzzle except an
   explicit quit — the puzzle is over, so there's nothing left for a
   normal command to mean."
  [line]
  (if (contains? #{"q" "quit"} (str/lower-case (str/trim (or line ""))))
    {:type :quit}
    {:type :next-puzzle}))

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
          (let [event (if (= (:status model) :won)
                        (won-screen-event line)
                        (parse/parse line))]
            (recur (update/update model event)))
          model)))))

(defn -main
  [& args]
  (let [seed (rand-int 1000000)
        arg (first args)
        difficulty (when arg (keyword arg))]
    (cond
      (nil? arg)
      (run (update/init-generated seed :easy))

      (contains? #{:easy :medium :hard} difficulty)
      (run (update/init-generated seed difficulty))

      (contains? puzzles/all difficulty)
      (run (update/init-fixed difficulty seed))

      :else
      (do
        (println "Unknown puzzle/difficulty:" arg)
        (println "Try: easy, medium, hard, or one of:"
                 (str/join ", " (map name (keys puzzles/all))))))))
