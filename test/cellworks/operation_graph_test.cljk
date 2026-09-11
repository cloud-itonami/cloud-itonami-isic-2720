(ns cellworks.operation-graph-test
  "Integration tests for `cellworks.operation/build` -- proves the REAL
  compiled `langgraph.graph` StateGraph runs end-to-end via
  `langgraph.graph/run*` through commit / hard-hold / escalate-approve /
  escalate-reject routes. No prior test file in this repo exercised
  `operation/build` at all -- every other test covers
  governor/phase/facts/registry/store/robotics in isolation, which
  proves those pure functions work but not that the graph wiring
  actually threads them together."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [cellworks.operation :as operation]
            [cellworks.store :as store]))

(def ^:private op-context {:actor-id "operator-01" :phase 3})

(defn- exec
  ([actor tid request] (exec actor tid request op-context))
  ([actor tid request context]
   (g/run* actor {:request request :context context} {:thread-id tid})))

(deftest commit-path-cell-batch-intake-auto-commits-in-phase-3
  (testing ":cell-batch/intake is the only op in phase-3's :auto set --
            a clean intake proposal commits straight through the REAL
            compiled graph with no interrupt, and the ledger is
            verified EMPTY before the run so the post-run fact is
            genuinely this run's own effect"
    (let [s (store/seed-db)
          actor (operation/build s)]
      (is (empty? (store/ledger s)))
      (let [result (exec actor "t-commit"
                         {:op :cell-batch/intake :subject "batch-test-1"
                          :patch {:id "batch-test-1" :batch-name "Test Cell Batch"
                                  :jurisdiction "UN" :status :intake}})
            state (:state result)]
        (is (= :done (:status result)))
        (is (= :commit (:disposition state)))
        (let [ledger (store/ledger s)]
          (is (= 1 (count ledger)))
          (is (= :committed (:t (first ledger))))
          (is (= :cell-batch/intake (:op (first ledger)))))
        (is (= "Test Cell Batch" (:batch-name (store/cell-batch s "batch-test-1"))))))))

(deftest hard-hold-end-of-line-defect-self-holds-on-screening
  (testing "batch-4's own :cell-batch-defect-unresolved? true means the
            :end-of-line-quality/screen proposal ITSELF reports
            :verdict :unresolved -- the governor HARD-holds on the
            screening op's own finding, proven end-to-end through the
            compiled graph"
    (let [s (store/seed-db)
          actor (operation/build s)
          result (exec actor "t-defecthold"
                       {:op :end-of-line-quality/screen :subject "batch-4"})
          state (:state result)]
      (is (= :done (:status result)))
      (is (= :hold (:disposition state)))
      (let [ledger (store/ledger s)]
        (is (= 1 (count ledger)))
        (is (some #{:end-of-line-defect-unresolved} (map :rule (:violations (first ledger)))))))))

(deftest phase-1-forces-escalate-on-otherwise-clean-commit
  (testing "phase-1 (assisted-intake) permits :cell-batch/intake as a
            write but never as auto-eligible -- proving the phase gate,
            not just the Governor, drives this graph's routing"
    (let [s (store/seed-db)
          actor (operation/build s)
          held (exec actor "t-phase1"
                     {:op :cell-batch/intake :subject "batch-1"
                      :patch {:batch-name "Meridian 21700 Cell Batch CB-4401"}}
                     (assoc op-context :phase 1))]
      (is (= :interrupted (:status held)))
      (is (= [:request-approval] (:frontier held)))
      (is (empty? (store/ledger s)) "not yet committed -- awaiting human sign-off"))))

(deftest phase-1-escalate-then-approve-commits
  (testing "resuming a phase-1-forced escalation with human approval
            genuinely completes the commit through the compiled graph's
            :request-approval -> :commit path"
    (let [s (store/seed-db)
          actor (operation/build s)
          held (exec actor "t-phase1-approve"
                     {:op :cell-batch/intake :subject "batch-2"
                      :patch {:batch-name "Atlas 21700 Cell Batch CB-1180"}}
                     (assoc op-context :phase 1))]
      (is (= :interrupted (:status held)))
      (let [approved (g/run* actor {:approval {:status :approved :by "quality-engineer-01"}}
                             {:thread-id "t-phase1-approve" :resume? true})
            approved-state (:state approved)]
        (is (= :done (:status approved)))
        (is (= :commit (:disposition approved-state)))
        (let [ledger (store/ledger s)]
          (is (= 1 (count ledger)))
          (is (= :committed (:t (first ledger)))))))))

(deftest phase-1-escalate-then-reject-holds
  (testing "a human quality engineer rejecting a phase-1-forced
            escalation routes to :hold via the :request-approval node's
            own decision, and durably records the rejection"
    (let [s (store/seed-db)
          actor (operation/build s)
          _held (exec actor "t-phase1-reject"
                      {:op :cell-batch/intake :subject "batch-5"
                       :patch {:batch-name "Test"}}
                      (assoc op-context :phase 1))
          rejected (g/run* actor {:approval {:status :rejected :by "quality-engineer-01"}}
                           {:thread-id "t-phase1-reject" :resume? true})
          rejected-state (:state rejected)]
      (is (= :done (:status rejected)))
      (is (= :hold (:disposition rejected-state)))
      (let [ledger (store/ledger s)]
        (is (= 1 (count ledger)))
        (is (= :approval-rejected (:t (first ledger))))))))
