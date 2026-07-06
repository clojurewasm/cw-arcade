(ns rush-hour.solver
  "A breadth-first search over the game's state graph. This is the payoff of
   keeping `:vehicles` a plain immutable map (rush-hour.board): a *state* is
   just a value, so a Clojure set is a correct, zero-ceremony visited-set —
   no identity, no hashCode/equals to write, no mutable graph of node
   objects to walk. Compare to an OOP board-of-mutable-objects design, where
   two boards being \"the same state\" has to be hand-defined and the
   explored set has to store snapshots/copies to avoid aliasing.

   BFS explores single-cell steps (rush-hour.board/legal-moves), so the
   move count reported here is in cell-steps, not `move` calls."
  (:require [rush-hour.board :as board]))

(defn solve
  "Shortest sequence of {:id :delta} steps from `vehicles` to a win, or nil
   if none exists within `max-depth` steps. BFS over a queue of
   [state path] pairs, held as a plain vector — dequeue from the front via
   `rest` rather than reaching for `clojure.lang.PersistentQueue` (a bare
   JVM-class reference we'd rather not depend on for a runtime-portable
   demo); `visited` dedupes purely by value equality."
  ([puzzle vehicles] (solve puzzle vehicles 200))
  ([puzzle vehicles max-depth]
   (loop [queue [[vehicles []]]
          visited #{vehicles}]
     (when-let [[state path] (first queue)]
       (cond
         (board/win? puzzle state) path
         (>= (count path) max-depth) (recur (vec (rest queue)) visited)
         :else
         (let [next-states (for [{:keys [id delta]} (board/legal-moves puzzle state)
                                  :let [{:keys [vehicles]} (board/move puzzle state id delta)]
                                  :when (not (visited vehicles))]
                              [vehicles (conj path {:id id :delta delta})])]
           (recur (into (vec (rest queue)) next-states)
                  (into visited (map first next-states)))))))))

(defn hint
  "Just the next step of `solve`'s path, or nil if unsolved/already solved."
  [puzzle vehicles]
  (first (solve puzzle vehicles)))
