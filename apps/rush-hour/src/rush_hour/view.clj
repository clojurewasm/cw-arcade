(ns rush-hour.view
  "The View half of the architecture: `render-plain` is a pure
   model -> string, nothing else — no printing, no ANSI, no side effects.
   `render` decorates the same layout with ANSI colour for the terminal.
   Splitting the two means the interesting one (the actual grid/status
   layout) is fully unit-testable as plain string content, and the terminal
   dressing is a thin, separately-swappable layer on top."
  (:require [clojure.string :as str]
            [rush-hour.board :as board]))

(defn- grid-rows
  "One string per board row: two characters per cell, then a space, bordered
   by '|', with the exit row's right border swapped for '>' — the one gap
   in the wall the target vehicle may cross. `cell-str` renders a single
   cell (a vehicle id or nil for empty) — the only place colour ever
   applies, so it can never bleed into the status/message lines below."
  [{:keys [width height exit-row]} vehicles cell-str]
  (let [occupied (board/occupied vehicles)]
    (for [r (range height)]
      (str "|"
           (str/join (for [c (range width)]
                       (str (cell-str (get occupied [r c])) " ")))
           (if (= r exit-row) ">" "|")))))

(defn- frame
  "Border + grid-rows + border + status + message — the layout shared by
   both the plain and the colourized render, parameterized only by the
   already-rendered row strings."
  [{:keys [puzzle-id puzzle moves status message selection]} rows]
  (let [{:keys [width]} puzzle
        border (str "+" (str/join (repeat (* 2 width) "-")) "+")]
    (str/join
     "\n"
     (concat [border]
             rows
             [border
              (str "Puzzle: " (name puzzle-id)
                   "   Moves: " moves
                   "   Selected: " (if selection (:id selection) "-")
                   (when (= status :won) "   *** SOLVED ***"))]
             (when message [(str "> " message)])))))

(defn- plain-cell [id] (or id "."))

(defn render-plain
  "The whole frame as plain text: border, grid, status line, and any
   message — everything a player needs, nothing a terminal doesn't
   understand."
  [{:keys [puzzle vehicles] :as model}]
  (frame model (grid-rows puzzle vehicles plain-cell)))

;; A real Rush Hour set casts each length-2 car and length-3 truck in a
;; fixed, limited run of colours (roughly a dozen car colours, four truck
;; colours), with the red car always the one that escapes. 256-colour SGR
;; (`38;5;N`) gives enough distinct hues to keep that faithfully — the
;; basic 8/16-colour ANSI set (what this app used before) can't tell 15
;; vehicles apart at once.
(def ^:private target-colour 196)                      ; red
(def ^:private truck-palette [208 94 30 55])            ; 4 truck colours
(def ^:private car-palette [226 46 21 201 51 213 118 178 39 129 245]) ; 11 car colours

(defn- vehicle-colours
  "id -> 256-colour index. Grouped by length (rush-hour.board vehicles, not
   this namespace, decide who's a car vs. a truck) so a truck always reads
   as one of the 4 truck hues and a car as one of the 11 car hues — the
   grouping is cosmetic only, board.clj has no notion of \"colour\"."
  [target vehicles]
  (let [by-length (group-by (fn [[id v]] (:length v))
                             (sort-by first (remove #(= target (first %)) vehicles)))
        assign (fn [pairs palette] (map vector (map first pairs) (cycle palette)))]
    (into {target target-colour}
          (concat (assign (get by-length 3) truck-palette)
                  (assign (get by-length 2) car-palette)))))

(def ^:private esc
  ;; Built from its codepoint rather than a string escape literal — keeps
  ;; this source file free of raw control bytes and sidesteps relying on
  ;; the host reader's unicode string-escape grammar for something this
  ;; load-bearing.
  (str (char 27)))

(defn- sgr
  "Wrap `text` in an ANSI SGR sequence — `params` is whatever goes before
   the closing `m` (e.g. \"38;5;208\", or \"1;38;5;208\" for bold)."
  [params text]
  (str esc "[" params "m" text esc "[0m"))

(defn- clear-screen []
  (str esc "[2J" esc "[H"))

(defn render
  "`render-plain`'s layout, with the terminal cleared/homed and each
   occupied cell colourized by vehicle id — bold for whichever vehicle is
   currently selected. Colour is applied per-cell while building the grid,
   never as a find/replace over the finished text — so it can't bleed into
   the status line or a message that happens to mention a vehicle id."
  [{:keys [puzzle vehicles selection] :as model}]
  (let [colours (vehicle-colours (:target puzzle) vehicles)
        selected-id (:id selection)
        cell (fn [id]
               (if id
                 (sgr (str (when (= id selected-id) "1;") "38;5;" (colours id)) id)
                 "."))
        rows (grid-rows puzzle vehicles cell)]
    (str (clear-screen) (frame model rows) "\n")))
