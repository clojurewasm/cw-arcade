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

(def default-max-states
  "A dense board's reachable-state graph can be enormous — measured one
   real 13-vehicle board that hadn't resolved after several *seconds* and
   hundreds of thousands of states. Capping total states visited (not just
   path depth) bounds worst-case latency to a small constant regardless of
   how gnarly a particular arrangement turns out to be; rush-hour.generator
   passes a tighter cap of its own during generate-and-test, since it needs
   to reject many candidates quickly, not exhaustively resolve each one."
  200000)

(defn solve
  "Shortest sequence of {:id :delta} steps from `vehicles` to a win, or nil
   if none exists within `max-depth` steps or `max-states` visited states
   (whichever comes first). BFS over a FIFO queue of [state path] pairs,
   held as two plain lists — `front` to pop from, `back` to push onto,
   refilling `front` with `(reverse back)` once it runs dry — the standard
   amortized-O(1) two-list queue. That matters here: a naive
   `(vec (rest queue))` re-pop makes each dequeue O(n) in the queue's size,
   so the whole search degrades to O(states²) once the state space gets
   into the thousands (a dense board easily does). Not
   `clojure.lang.PersistentQueue` (a bare JVM-class reference we'd rather
   not depend on for a runtime-portable demo); `visited` dedupes purely by
   value equality."
  ([puzzle vehicles] (solve puzzle vehicles 200))
  ([puzzle vehicles max-depth] (solve puzzle vehicles max-depth default-max-states))
  ([puzzle vehicles max-depth max-states]
   (loop [front (list [vehicles []])
          back ()
          visited #{vehicles}]
     (cond
       (and (empty? front) (empty? back)) nil
       (>= (count visited) max-states) nil
       (empty? front) (recur (reverse back) () visited)
       :else
       (let [[state path] (first front)
             front (rest front)]
         (cond
           (board/win? puzzle state) path
           (>= (count path) max-depth) (recur front back visited)
           :else
           (let [next-states (for [{:keys [id delta]} (board/legal-moves puzzle state)
                                    :let [{:keys [vehicles]} (board/move puzzle state id delta)]
                                    :when (not (visited vehicles))]
                                [vehicles (conj path {:id id :delta delta})])]
             (recur front
                    (into back next-states)
                    (into visited (map first next-states))))))))))

(defn hint
  "Just the next step of `solve`'s path, or nil if unsolved/already solved."
  [puzzle vehicles]
  (first (solve puzzle vehicles)))
