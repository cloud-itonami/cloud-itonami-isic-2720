(ns cellworks.facts-test
  (:require [clojure.test :refer [deftest is]]
            [cellworks.facts :as facts]))

(deftest un-has-a-spec-basis
  (is (some? (facts/spec-basis "UN")))
  (is (string? (:provenance (facts/spec-basis "UN")))))

(deftest usa-chn-iec-have-spec-bases
  (is (some? (facts/spec-basis "USA")))
  (is (some? (facts/spec-basis "CHN")))
  (is (some? (facts/spec-basis "IEC"))))

(deftest unknown-jurisdiction-has-no-fabricated-spec-basis
  (is (nil? (facts/spec-basis "ATL"))))

(deftest coverage-never-reports-a-missing-jurisdiction-as-covered
  (let [report (facts/coverage ["UN" "ATL" "USA"])]
    (is (= 2 (:covered report)))
    (is (= ["ATL"] (:missing-jurisdictions report)))
    (is (= ["UN" "USA"] (:covered-jurisdictions report)))))

(deftest required-evidence-satisfied-needs-every-item
  (let [all (facts/evidence-checklist "UN")]
    (is (facts/required-evidence-satisfied? "UN" all))
    (is (not (facts/required-evidence-satisfied? "UN" (rest all))))
    (is (not (facts/required-evidence-satisfied? "ATL" all)) "no spec-basis -> never satisfied")))
