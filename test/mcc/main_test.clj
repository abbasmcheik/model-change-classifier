(ns mcc.main-test
  "End-to-end tests of `run` on the example cards. We never call -main here,
  because it ends with System/exit and would kill the test JVM."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [mcc.main :as main]))

(defn- run-example
  "Runs base.edn against the example `variant` (a file name in examples/)."
  [variant]
  (main/run ["examples/base.edn" (str "examples/" variant)]))

(defn- one-line-error?
  "True when `result` is a clean input failure: exit 3, no stdout, and a
  non-empty single-line stderr without any exception text."
  [{:keys [exit out err]}]
  (and (= 3 exit)
       (= "" out)
       (seq err)
       (not (str/includes? err "\n"))
       (not (str/includes? err "Exception"))))

(deftest base-against-base-test
  (let [result (run-example "base.edn")]
    (is (= 0 (:exit result)))
    (is (str/starts-with? (:out result) "Verdict: MINOR"))
    (is (str/includes? (:out result) "No changes."))
    (is (= "" (:err result)))))

(deftest minor-example-test
  (let [result (run-example "minor.edn")]
    (is (= 0 (:exit result)))
    (is (str/starts-with? (:out result) "Verdict: MINOR"))
    (is (str/includes? (:out result) "version"))))

(deftest notify-example-test
  (let [result (run-example "notify.edn")]
    (is (= 1 (:exit result)))
    (is (str/starts-with? (:out result) "Verdict: NOTIFY"))
    (is (str/includes? (:out result) "deployment.platforms"))))

(deftest reassess-example-test
  (let [result (run-example "reassess.edn")]
    (is (= 2 (:exit result)))
    (is (str/starts-with? (:out result) "Verdict: REASSESS"))
    (is (str/includes? (:out result) "performance.sensitivity"))
    (is (str/includes? (:out result) "performance-drop-large"))))

(deftest unknown-field-example-test
  (let [result (run-example "unknown-field.edn")]
    (is (= 2 (:exit result)))
    (is (str/starts-with? (:out result) "Verdict: REASSESS"))
    (is (str/includes? (:out result) "regulatory-note"))
    (is (str/includes? (:out result) "(no rule)"))))

(deftest invalid-card-test
  (testing "invalid new card"
    (let [result (run-example "invalid.edn")]
      (is (one-line-error? result))
      (is (str/includes? (:err result) "invalid.edn"))))
  (testing "invalid old card"
    (let [result (main/run ["examples/invalid.edn" "examples/base.edn"])]
      (is (one-line-error? result))
      (is (str/includes? (:err result) "invalid.edn")))))

(deftest wrong-argument-count-test
  (doseq [args [[] ["examples/base.edn"]
                ["examples/base.edn" "examples/minor.edn" "examples/notify.edn"]]]
    (let [result (main/run args)]
      (is (one-line-error? result))
      (is (str/starts-with? (:err result) "usage:")))))

(deftest missing-file-test
  (let [result (main/run ["examples/base.edn" "examples/does-not-exist.edn"])]
    (is (one-line-error? result))
    (is (str/includes? (:err result) "does-not-exist.edn"))))

(deftest unparsable-edn-test
  (let [tmp (java.io.File/createTempFile "mcc-bad" ".edn")]
    (try
      (spit tmp "{:name \"ExampleDR\" :version")   ; unterminated map
      (let [result (main/run ["examples/base.edn" (.getPath tmp)])]
        (is (one-line-error? result))
        (is (str/includes? (:err result) "cannot parse EDN")))
      (finally
        (.delete tmp)))))
