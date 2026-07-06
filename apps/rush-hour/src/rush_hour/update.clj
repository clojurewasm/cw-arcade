(ns rush-hour.update
  "The Model/Update half of an Elm-Architecture-shaped app (see the app
   README for the full rationale): `init-fixed`/`init-generated` build the
   single state value, `update` is `(model, event) -> model'` and never
   touches a terminal, never reads a clock, never does I/O — even the
   puzzle generator's randomness only ever arrives as a `:seed` already
   sitting in the model, never minted here. Every branch is a plain data
   transformation, which is what lets rush-hour.update-test exercise the
   entire game (selection, undo, win, puzzle generation) without a screen
   or a keyboard.

   Selection model: at most one vehicle is ever \"picked up\"
   (`:selection {:id .. :origin-row .. :origin-col ..}`). Nudging it moves
   the board but never touches the move counter — the counter only ticks
   when the player commits to that pick-up by leaving it (selecting a
   different vehicle, deselecting, or winning) and the vehicle actually
   ended up somewhere other than `:origin-row`/`:origin-col`. Wiggling a
   vehicle back to exactly where you grabbed it, however many nudges that
   took, costs nothing — that comparison is always against the live
   `:vehicles` position, so it stays correct through any number of undos
   too."
  (:refer-clojure :exclude [update])
  (:require [rush-hour.board :as board]
            [rush-hour.generator :as generator]
            [rush-hour.puzzles :as puzzles]
            [rush-hour.solver :as solver]))

(defn- base-model
  [puzzle-id puzzle difficulty seed message]
  {:puzzle-id puzzle-id
   :puzzle puzzle
   :vehicles (:vehicles puzzle)
   :selection nil
   :history []
   :moves 0
   :status :playing
   :difficulty difficulty
   :seed seed
   :message message})

(defn init-fixed
  "The starting Model for one of the hand-built puzzles (rush-hour.puzzles).
   `seed` is carried along unused until the player asks for a generated
   puzzle (`p`) — it has to come from somewhere pure-data, and the
   imperative shell is the only place allowed to mint a fresh one."
  [puzzle-id seed]
  (base-model puzzle-id (get puzzles/all puzzle-id) nil seed
              (str "Puzzle \"" (name puzzle-id) "\" loaded. Type \"?\" for help.")))

(defn init-generated
  "The starting Model for a freshly generated puzzle at `difficulty`,
   deterministic in `seed`."
  [seed difficulty]
  (let [puzzle (generator/generate seed difficulty)]
    (base-model difficulty puzzle difficulty (generator/next-seed seed)
                (str "New " (name difficulty) " puzzle."))))

(defn- next-generated
  "Used both for the explicit `p` command and for auto-advancing after a
   win: same difficulty if one was already active (generated play just
   continues), else :easy (a fixed tutorial puzzle graduates into endless
   generated play)."
  [{:keys [seed difficulty]}]
  (init-generated seed (or difficulty :easy)))

(defn- restart
  "Back to this puzzle's own starting layout — `:puzzle` always holds the
   original board regardless of whether it came from rush-hour.puzzles or
   the generator, so this needs no branch on which."
  [{:keys [puzzle] :as model}]
  (assoc model
         :vehicles (:vehicles puzzle)
         :selection nil :history [] :moves 0 :status :playing
         :message "Restarted."))

(defn- moved-since-select?
  [vehicles {:keys [id origin-row origin-col]}]
  (let [{:keys [row col]} (get vehicles id)]
    (or (not= row origin-row) (not= col origin-col))))

(defn- finalize-selection
  "Bank the current selection's move (if it actually moved) right before
   the player leaves it — the one place the move counter ever changes
   outside of winning."
  [{:keys [vehicles selection moves] :as model}]
  (if (and selection (moved-since-select? vehicles selection))
    (assoc model :moves (inc moves))
    model))

(defn- select
  [{:keys [vehicles selection] :as model} id]
  (cond
    (= id (:id selection)) model ; reselecting what's already held: a no-op

    (not (contains? vehicles id))
    (assoc model :message (str "No vehicle " id "."))

    :else
    (let [{:keys [row col]} (get vehicles id)]
      (-> model
          finalize-selection
          (assoc :selection {:id id :origin-row row :origin-col col}
                 :message nil)))))

(defn- deselect
  [model]
  (-> model finalize-selection (assoc :selection nil :message nil)))

(defn- axis-delta
  "nil (not just a wrong-axis message) when `dir` doesn't match
   `orientation` — the caller treats that as \"nothing happens\", matching
   how a wrong arrow key does nothing in any other game."
  [orientation dir steps]
  (case [orientation dir]
    [:h :left] (- steps)
    [:h :right] steps
    [:v :up] (- steps)
    [:v :down] steps
    nil))

(defn- nudge
  [{:keys [puzzle vehicles selection history moves] :as model} dir steps]
  (if-not selection
    (assoc model :message "Select a vehicle first — any letter (x is the escaping car).")
    (let [{:keys [id]} selection
          {:keys [orientation]} (get vehicles id)
          delta (axis-delta orientation dir steps)]
      (if (nil? delta)
        model ; wrong axis for this vehicle: truly nothing happens
        (let [{:keys [ok? vehicles message]} (board/move puzzle vehicles id delta)]
          (if ok?
            (let [won? (board/win? puzzle vehicles)]
              (assoc model
                     :vehicles vehicles
                     :history (conj history (:vehicles model))
                     :status (if won? :won :playing)
                     :moves (if won? (inc moves) moves)
                     :message (when won? (str "*** Solved in " (inc moves) " moves! ***"))))
            (assoc model :message message)))))))

(defn- undo
  "Reverts the board one nudge at a time. Deliberately does not touch the
   move counter — that counts committed selection-switches, not raw board
   edits, and the two are allowed to disagree (undo enough and you can
   \"un-commit\" a move whose count already got banked). Simpler than
   snapshotting the counter alongside every nudge, and the counter was
   only ever a progress indicator, not a puzzle-integrity guarantee."
  [{:keys [history] :as model}]
  (if (seq history)
    (assoc model
           :vehicles (peek history)
           :history (pop history)
           :status :playing
           :message "Undid last move.")
    (assoc model :message "Nothing to undo yet.")))

(defn- delta->letter
  [orientation delta]
  (case [orientation (pos? delta)]
    [:h true] "l" [:h false] "h"
    [:v true] "j" [:v false] "k"))

(defn- describe-move
  "{:id :delta} -> what a player would type for it: select the vehicle,
   then the one-letter nudge — matching rush-hour.solver's single-cell-step
   granularity exactly, so a hint really is just \"do this next\"."
  [vehicles {:keys [id delta]}]
  (str id ", then " (delta->letter (:orientation (get vehicles id)) delta)))

(defn- hint
  [{:keys [puzzle vehicles] :as model}]
  (if-let [step (solver/hint puzzle vehicles)]
    (assoc model :message (str "Hint: " (describe-move vehicles step)))
    (assoc model :message "No hint to give — already solved, or truly stuck.")))

(def help-text
  (str "Select a vehicle by its letter (x is the escaping car).\n"
       "  h/j/k/l  nudge it left/down/up/right — a leading count works, e.g. 5j\n"
       "  0 deselect (no penalty) · z undo · r restart · p new puzzle\n"
       "  s hint · q quit\n"
       "A move only counts once you switch away from a vehicle you actually moved."))

(defn update
  "(model, event) -> model'. The one place game rules meet game state."
  [model {:keys [type id dir steps message]}]
  (case type
    :select (select model id)
    :deselect (deselect model)
    :nudge (nudge model dir steps)
    :undo (undo model)
    :restart (restart model)
    :next-puzzle (next-generated model)
    :hint (hint model)
    :help (assoc model :message help-text)
    :quit (assoc model :status :quit)
    :noop model
    :error (assoc model :message message)))
