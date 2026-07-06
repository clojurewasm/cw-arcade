(ns rush-hour.board
  "Pure board/vehicle data model. No I/O, no atoms — every fn here is
   (data...) -> data, which is what makes rush-hour.solver's plain `visited`
   set (just a Clojure set of maps) correct: two states are `=` exactly when
   every vehicle occupies the same cells, so structural equality on plain
   maps is the whole state-dedup mechanism.

   A puzzle is a map:
     {:width 6 :height 6 :exit-row 2 :target \"X\"
      :vehicles {\"X\" {:orientation :h :length 2 :row 2 :col 3} ...}}

   `:vehicles` is the only part that changes as the game is played; the rest
   travel alongside it unchanged.")

(defn cells
  "Every [row col] cell a vehicle occupies."
  [{:keys [orientation length row col]}]
  (case orientation
    :h (for [dc (range length)] [row (+ col dc)])
    :v (for [dr (range length)] [(+ row dr) col])))

(defn occupied
  "cell -> vehicle-id, over every vehicle except `ignore-id` (if given)."
  ([vehicles] (occupied vehicles nil))
  ([vehicles ignore-id]
   (into {}
         (mapcat (fn [[id v]] (map (fn [c] [c id]) (cells v))))
         (dissoc vehicles ignore-id))))

(defn- axis-delta
  "A vehicle only moves along its own axis: [row col] -> the coordinate that
   changes when it slides by `delta`, all others held fixed."
  [{:keys [orientation row col]} delta]
  (case orientation
    :h {:row row :col (+ col delta)}
    :v {:row (+ row delta) :col col}))

(defn- in-bounds-cell?
  [{:keys [width height]} [r c]]
  (and (<= 0 r) (< r height) (<= 0 c) (< c width)))

(defn- exiting?
  "True once a cell is riding through the exit gap (the target's row, at or
   past the right wall) — the one place a vehicle is allowed off-grid."
  [{:keys [exit-row width]} [r c]]
  (and (= r exit-row) (>= c width)))

(defn win?
  "Solved once every cell of the target vehicle has cleared the board
   through the exit — not merely touching the wall."
  [{:keys [target width]} vehicles]
  (every? #(>= (second %) width) (cells (get vehicles target))))

(defn move
  "Attempt to slide vehicle `id` by `delta` cells along its own axis
   (positive = right/down, negative = left/up). Returns
     {:ok? true  :vehicles <new-vehicles>}
   or
     {:ok? false :reason <keyword> :message <string>}
   Errors are plain data, not exceptions — a move is just as likely as not
   to be rejected during play, so it isn't exceptional control flow.

   Checked one cell-step at a time, and at each step the vehicle's *whole*
   footprint (not just its anchor cell) is re-derived via `cells` and
   checked — simple and obviously correct, over being clever about which
   single edge cell changed."
  [{:keys [target] :as puzzle} vehicles id delta]
  (if-let [v (get vehicles id)]
    (if (zero? delta)
      {:ok? false :reason :no-op :message "That's not a move."}
      (let [others (occupied vehicles id)
            target? (= id target)
            step (if (pos? delta) 1 -1)
            offsets (range step (+ delta step) step)
            problem (some (fn [d]
                            (let [v' (merge v (axis-delta v d))]
                              (some (fn [cell]
                                      (cond
                                        (and target? (exiting? puzzle cell)) nil
                                        (not (in-bounds-cell? puzzle cell))
                                        {:reason :out-of-bounds
                                         :message (str id " can't leave the grid.")}
                                        (contains? others cell)
                                        {:reason :blocked
                                         :message (str id " is blocked by " (others cell) ".")}
                                        :else nil))
                                    (cells v'))))
                          offsets)]
        (if problem
          (assoc problem :ok? false)
          {:ok? true :vehicles (assoc vehicles id (merge v (axis-delta v delta)))})))
    {:ok? false :reason :no-such-vehicle :message (str "No vehicle " id ".")}))

(defn legal-moves
  "Every {:id :delta} that `move` would accept from this position — the
   solver's neighbour function, expressed as single-cell steps."
  [puzzle vehicles]
  (for [[id _] vehicles
        delta [-1 1]
        :let [result (move puzzle vehicles id delta)]
        :when (:ok? result)]
    {:id id :delta delta}))
