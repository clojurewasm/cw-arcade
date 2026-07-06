(ns rush-hour.update
  "The Model/Update half of an Elm-Architecture-shaped app (see the
   app README for the full rationale): `init` builds the single state value,
   `update` is `(model, event) -> model'` and never touches a terminal,
   never reads a clock, never does I/O. Every branch is a plain data
   transformation, which is what lets rush-hour.update-test exercise the
   entire game without a screen or a keyboard."
  (:refer-clojure :exclude [update])
  (:require [rush-hour.board :as board]
            [rush-hour.puzzles :as puzzles]
            [rush-hour.solver :as solver]))

(def ^:private puzzle-order [:warm-up :gridlock])

(defn init
  "The starting Model for a puzzle id."
  [puzzle-id]
  (let [puzzle (get puzzles/all puzzle-id)]
    {:puzzle-id puzzle-id
     :puzzle puzzle
     :vehicles (:vehicles puzzle)
     :history []
     :moves 0
     :status :playing
     :message (str "Puzzle \"" (name puzzle-id) "\" loaded. Type \"help\" for commands.")}))

(defn- dir->delta
  "A direction letter only means something combined with the vehicle's own
   axis: :l/:r move a horizontal vehicle, :u/:d move a vertical one. Any
   other pairing is a user mistake, reported as data rather than ignored."
  [orientation dir steps]
  (case [orientation dir]
    [:h :l] (- steps)
    [:h :r] steps
    [:v :u] (- steps)
    [:v :d] steps
    :mismatch))

(defn- describe-move
  "{:id :delta} -> the command string a player would type for it, so a hint
   reads like something you could type back in."
  [vehicles {:keys [id delta]}]
  (let [{:keys [orientation]} (get vehicles id)
        dir (case [orientation (pos? delta)]
              [:h true] "r" [:h false] "l"
              [:v true] "d" [:v false] "u")]
    (str id " " dir (max delta (- delta)))))

(defn- apply-move
  [{:keys [puzzle vehicles history moves] :as model} {:keys [id dir steps]}]
  (if-let [{:keys [orientation]} (get vehicles id)]
    (let [delta (dir->delta orientation dir steps)]
      (if (= delta :mismatch)
        (assoc model :message (str id " can't move that way."))
        (let [{:keys [ok? vehicles message]} (board/move puzzle vehicles id delta)]
          (if ok?
            (let [won? (board/win? puzzle vehicles)]
              (assoc model
                     :vehicles vehicles
                     :history (conj history (:vehicles model))
                     :moves (inc moves)
                     :status (if won? :won :playing)
                     :message (if won?
                                (str "*** Solved in " (inc moves) " moves! ***")
                                nil)))
            (assoc model :message message)))))
    (assoc model :message (str "No vehicle " id "."))))

(defn- undo
  [{:keys [history] :as model}]
  (if (seq history)
    (let [vehicles (peek history)]
      (assoc model
             :vehicles vehicles
             :history (pop history)
             :moves (max 0 (dec (:moves model)))
             :status :playing
             :message "Undid last move."))
    (assoc model :message "Nothing to undo yet.")))

(defn- restart
  [{:keys [puzzle-id]}]
  (assoc (init puzzle-id) :message "Restarted."))

(defn- next-puzzle
  [{:keys [puzzle-id]}]
  (let [current (first (keep-indexed #(when (= %2 puzzle-id) %1) puzzle-order))
        next-id (nth puzzle-order (mod (inc current) (count puzzle-order)))]
    (init next-id)))

(defn- hint
  [{:keys [puzzle vehicles] :as model}]
  (if-let [step (solver/hint puzzle vehicles)]
    (assoc model :message (str "Hint: " (describe-move vehicles step)))
    (assoc model :message "No hint to give — already solved, or truly stuck.")))

(def help-text
  (str "Commands: <vehicle><dir><n>  e.g. Xr2 (X right 2), Bu1 (B up 1)\n"
       "          l/r/u/d = left/right/up/down, n defaults to 1\n"
       "          u undo · r restart · n next puzzle · s hint · q quit"))

(defn update
  "(model, event) -> model'. The one place game rules meet game state."
  [model {:keys [type] :as event}]
  (case type
    :move (apply-move model event)
    :undo (undo model)
    :restart (restart model)
    :next-puzzle (next-puzzle model)
    :hint (hint model)
    :help (assoc model :message help-text)
    :quit (assoc model :status :quit)
    :noop model
    :error (assoc model :message (:message event))))
