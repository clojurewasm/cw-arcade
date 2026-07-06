(ns rush-hour.parse
  "Raw input line -> event map. Pure string -> data, deliberately kept
   ignorant of any particular puzzle or vehicle — it doesn't know whether
   \"X\" exists or which way it's allowed to slide. That's rush-hour.update's
   job. Keeping the grammar here means it's testable without a board at
   all.")

(def ^:private move-re #"(?i)^([a-z])([lrud])(\d*)$")

(def ^:private dir->keyword {\l :l \r :r \u :u \d :d})

(defn parse
  "A line of user input -> an event map. Unrecognised input is
   {:type :error :message ...} — data, so the caller decides how loud to be
   about it, rather than an exception unwinding the input loop."
  [line]
  (let [s (clojure.string/trim (or line ""))]
    (cond
      (clojure.string/blank? s) {:type :noop}

      (#{"q" "quit"} (clojure.string/lower-case s)) {:type :quit}
      (#{"u" "undo"} (clojure.string/lower-case s)) {:type :undo}
      (#{"r" "restart" "reset"} (clojure.string/lower-case s)) {:type :restart}
      (#{"n" "next"} (clojure.string/lower-case s)) {:type :next-puzzle}
      (#{"s" "hint" "solve"} (clojure.string/lower-case s)) {:type :hint}
      (#{"h" "help" "?"} (clojure.string/lower-case s)) {:type :help}

      :else
      (if-let [[_ id dir steps] (re-matches move-re s)]
        {:type :move
         :id (clojure.string/upper-case id)
         :dir (dir->keyword (first (clojure.string/lower-case dir)))
         :steps (if (clojure.string/blank? steps) 1 (read-string steps))}
        {:type :error :message (str "Don't understand \"" s "\". Try \"help\".")}))))
