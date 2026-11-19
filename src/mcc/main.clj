(ns mcc.main
  "Command line entry point. `run` is pure (returns data), `-main` does the
  printing and the System/exit."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [mcc.classify :as classify]
            [mcc.diff :as diff]
            [mcc.report :as report]
            [mcc.rules :as rules]
            [mcc.spec :as spec]))

(def usage "usage: clojure -M:run old.edn new.edn")

(def exit-codes
  "Process exit code for each verdict."
  {:minor 0 :notify 1 :reassess 2})

(defn- first-line
  "First line of `s`, so every error message stays on one line."
  [s]
  (first (str/split-lines (str s))))

(defn- fail
  "A failed run: exit 3, nothing on stdout, a one-line message on stderr."
  [message]
  {:exit 3 :out "" :err (first-line message)})

(defn- read-card
  "Reads one EDN file. Returns {:card value} or {:error message}.
  Only I/O problems (missing file) and EDN syntax errors are caught."
  [path]
  (try
    (let [text (slurp path)]
      (try
        {:card (edn/read-string text)}
        ;; the EDN reader throws RuntimeException subclasses on bad syntax
        (catch RuntimeException e
          {:error (str path ": cannot parse EDN: " (ex-message e))})))
    (catch java.io.IOException _
      {:error (str path ": cannot read file")})))

(defn- load-valid-card
  "Reads `path` and checks it against the card spec.
  Returns {:card value} or {:error message}."
  [path]
  (let [{:keys [card error] :as result} (read-card path)]
    (if error
      result
      (if-let [problem (spec/explain-card card)]
        {:error (str path ": invalid card: " (first-line problem))}
        result))))

(defn- compare-cards
  "Diffs two valid cards, classifies the changes, and builds the success result."
  [old-card new-card]
  (let [result (classify/classify-changes rules/rules
                                          (diff/diff-cards old-card new-card))]
    {:exit (exit-codes (:verdict result))
     :out (report/->text result)
     :err ""}))

(defn run
  "Runs the classifier on `args` (exactly two file paths: old.edn new.edn).
  Returns {:exit 0..3 :out string :err string}. Pure: no printing, no exit."
  [args]
  (if-not (= 2 (count args))
    (fail usage)
    (let [[old-path new-path] args
          old-loaded (load-valid-card old-path)
          new-loaded (load-valid-card new-path)]
      (cond
        (:error old-loaded) (fail (:error old-loaded))
        (:error new-loaded) (fail (:error new-loaded))
        :else (compare-cards (:card old-loaded) (:card new-loaded))))))

(defn -main
  "Calls `run`, prints stdout and stderr, then exits with the result's code."
  [& args]
  (let [{:keys [exit out err]} (run args)]
    (when (seq out) (println out))
    (when (seq err) (binding [*out* *err*] (println err)))
    (flush)
    (binding [*out* *err*] (flush))
    (System/exit exit)))
