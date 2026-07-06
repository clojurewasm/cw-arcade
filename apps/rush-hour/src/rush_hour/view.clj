(ns rush-hour.view
  "The View half of the architecture: `render-plain` is a pure
   model -> string, nothing else — no printing, no ANSI, no side effects.
   `render` decorates that same structure with ANSI colour for the terminal.
   Splitting the two means the interesting one (the actual grid/status
   layout) is fully unit-testable as plain string content, and the terminal
   dressing is a thin, separately-swappable layer on top."
  (:require [clojure.string :as str]
            [rush-hour.board :as board]))

(defn- grid-rows
  "One string per board row: two characters per cell (id or '.', then a
   space), bordered by '|', with the exit row's right border swapped for
   '>' — the one gap in the wall the target vehicle may cross."
  [{:keys [width height exit-row]} vehicles]
  (let [occupied (board/occupied vehicles)]
    (for [r (range height)]
      (str "|"
           (str/join (for [c (range width)]
                       (str (get occupied [r c] ".") " ")))
           (if (= r exit-row) ">" "|")))))

(defn render-plain
  "The whole frame as plain text: border, grid, status line, and any
   message — everything a player needs, nothing a terminal doesn't
   understand."
  [{:keys [puzzle-id puzzle vehicles moves status message]}]
  (let [{:keys [width]} puzzle
        border (str "+" (str/join (repeat (* 2 width) "-")) "+")]
    (str/join
     "\n"
     (concat [border]
             (grid-rows puzzle vehicles)
             [border
              (str "Puzzle: " (name puzzle-id) "   Moves: " moves
                   (when (= status :won) "   *** SOLVED ***"))]
             (when message [(str "> " message)])))))

(def ^:private palette
  ;; ANSI SGR colour codes cycled across non-target vehicles; the target
  ;; is always red regardless of id, since that's the one every Rush Hour
  ;; player already expects.
  [36 33 32 35 34])

(defn- vehicle-colours
  [target vehicles]
  (let [others (sort (remove #(= % target) (keys vehicles)))]
    (into {target 31}
          (map vector others (cycle palette)))))

(def ^:private esc
  ;; Built from its codepoint rather than a string escape literal — keeps
  ;; this source file free of raw control bytes and sidesteps relying on
  ;; the host reader's unicode string-escape grammar for something this
  ;; load-bearing.
  (str (char 27)))

(defn- sgr
  "Wrap `text` in an ANSI SGR colour code."
  [code text]
  (str esc "[" code "m" text esc "[0m"))

(defn- colourize-line
  [colours line]
  (reduce (fn [s [id code]] (str/replace s (str id) (sgr code (str id))))
          line
          colours))

(defn- clear-screen []
  (str esc "[2J" esc "[H"))

(defn render
  "`render-plain`'s frame, with the terminal cleared/homed and each vehicle
   colourized. This is the only view fn a terminal ever needs to see; the
   game logic never calls it."
  [{:keys [puzzle vehicles] :as model}]
  (let [colours (vehicle-colours (:target puzzle) vehicles)]
    (str (clear-screen)
         (colourize-line colours (render-plain model))
         "\n")))
