(ns rush-hour.parse
  "Raw input line -> event map. Pure string -> data, deliberately kept
   ignorant of any particular puzzle or vehicle — it doesn't know whether
   \"A\" exists or which way it's allowed to slide. That's rush-hour.update's
   job. Keeping the grammar here means it's testable without a board at
   all.

   The alphabet is partitioned so no character means two things:
     h j k l   move the selected vehicle (vim-style; an optional leading
               count works the same way vim's count-prefix does, e.g. 5j)
     z r p s q undo / restart / next (generated) puzzle / hint / quit
     0         deselect
     ?         help
     anything else single-letter (a-z, so also x)  select that vehicle
   Every one of those is disjoint from the others by construction — see
   rush-hour.generator's id pool, which is exactly \"every letter except
   h j k l z r p s q x\"."
  (:require [clojure.string :as str]))

(def ^:private nudge-re #"(?i)^(\d*)([hjkl])$")

;; Named so they can never be confused with rush-hour.board's :h/:v
;; orientation keywords, even though both alphabets happen to use "h".
(def ^:private letter->dir {\h :left \j :down \k :up \l :right})

(def ^:private command-words
  {"q" :quit "z" :undo "r" :restart "p" :next-puzzle "s" :hint
   "?" :help "0" :deselect})

(defn parse
  "A line of user input -> an event map. Unrecognised input is
   {:type :error :message ...} — data, so the caller decides how loud to be
   about it, rather than an exception unwinding the input loop."
  [line]
  (let [s (str/trim (or line ""))
        lower (str/lower-case s)]
    (cond
      (str/blank? s) {:type :noop}

      (contains? command-words lower) {:type (command-words lower)}

      :else
      (if-let [[_ steps dir] (re-matches nudge-re s)]
        {:type :nudge
         :dir (letter->dir (first (str/lower-case dir)))
         :steps (if (str/blank? steps) 1 (read-string steps))}
        (if (re-matches #"(?i)^[a-z]$" s)
          {:type :select :id (str/upper-case s)}
          {:type :error :message (str "Don't understand \"" s "\". Try \"?\" for help.")})))))
