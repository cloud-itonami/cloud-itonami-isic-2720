(ns cellworks.governor-contract-test
  "The governor contract as executable tests -- the battery-plant
  analog of `cloud-itonami-isic-6512`'s `casualty.governor-contract-
  test`. The single invariant under test:

    Cell Advisor never ships a cell-batch action or issues a Battery
    Safety Test Report the Cell-Safety Governor would reject,
    `:actuation/ship-cell-batch`/`:actuation/issue-safety-certificate`
    NEVER auto-commit at any phase, `:cell-batch/intake` (no direct
    capital risk) MAY auto-commit when clean, and every decision
    (commit OR hold) leaves exactly one ledger fact."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [cellworks.store :as store]
            [cellworks.operation :as op]))

(defn- fresh []
  (let [db (store/seed-db)]
    [db (op/build db)]))

(def operator {:actor-id "op-1" :actor-role :quality-engineer :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn- verify!
  "Walks `subject` through verify -> approve, leaving a cell-safety-
  rules evidence verification on file. Uses distinct thread-ids per
  call site by suffixing `tid-prefix`."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-verify") {:op :cell-safety-rules/verify :subject subject} operator)
  (approve! actor (str tid-prefix "-verify")))

(defn- screen!
  "Walks `subject` through end-of-line quality screening -> approve,
  leaving a screening on file. Only safe to call for a cell-batch whose
  defect status has already resolved -- an unresolved defect HARD-holds
  the screen itself (see `end-of-line-defect-is-held-and-unoverridable`)."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-screen") {:op :end-of-line-quality/screen :subject subject} operator)
  (approve! actor (str tid-prefix "-screen")))

(defn- simulate-robotics!
  "Walks `subject` through the robot UN 38.3 T6 crush-test verification
  mission -> approve, leaving `:robotics-sim-verified?` on file. Only
  meaningful to call for a cell-batch whose REAL `physics-2d`-simulated
  crush-force telemetry (`:sim-peak-crush-force-n`) is actually within
  UN 38.3 T6's 13kN ceiling -- a cell-batch whose crush-run
  configuration exceeds it still gets :robotics-sim-verified? recorded
  (per whatever the mission itself found), but `cellworks.governor`'s
  independent recheck HARD-holds regardless (see
  `robotics-simulation-out-of-tolerance-is-held`)."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-robotics") {:op :robotics/simulate-crush-test :subject subject} operator)
  (approve! actor (str tid-prefix "-robotics")))

(deftest clean-intake-auto-commits
  (let [[db actor] (fresh)
        res (exec-op actor "t1"
                  {:op :cell-batch/intake :subject "batch-1"
                   :patch {:id "batch-1" :batch-name "Meridian 21700 Cell Batch CB-4401"}} operator)]
    (is (= :commit (get-in res [:state :disposition])))
    (is (= "Meridian 21700 Cell Batch CB-4401" (:batch-name (store/cell-batch db "batch-1"))) "SSoT actually updated")
    (is (= 1 (count (store/ledger db))))))

(deftest requirements-verify-always-needs-approval
  (testing "verify is never in any phase's :auto set -- always human approval, even when clean"
    (let [[db actor] (fresh)
          res (exec-op actor "t2" {:op :cell-safety-rules/verify :subject "batch-1"} operator)]
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "t2")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (some? (store/cell-safety-verification-of db "batch-1")))))))

(deftest fabricated-jurisdiction-is-held
  (testing "a cell-safety-rules/verify proposal with no official spec-basis -> HOLD, never reaches a human"
    (let [[db actor] (fresh)
          res (exec-op actor "t3"
                    {:op :cell-safety-rules/verify :subject "batch-2"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:no-spec-basis} (-> (store/ledger db) first :basis)))
      (is (nil? (store/cell-safety-verification-of db "batch-2")) "no verification written"))))

(deftest ship-cell-batch-without-verification-is-held
  (testing "actuation/ship-cell-batch before any cell-safety-rules evidence verification -> HOLD (evidence incomplete)"
    (let [[db actor] (fresh)
          res (exec-op actor "t4" {:op :actuation/ship-cell-batch :subject "batch-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:evidence-incomplete} (-> (store/ledger db) first :basis))))))

(deftest cell-batch-resistance-out-of-range-is-held
  (testing "a cell-batch whose own internal-resistance deviation falls outside its own acceptance-band bounds -> HOLD"
    (let [[db actor] (fresh)
          _ (verify! actor "t5pre" "batch-3")
          _ (simulate-robotics! actor "t5pre2" "batch-3")
          res (exec-op actor "t5" {:op :actuation/ship-cell-batch :subject "batch-3"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:cell-batch-resistance-out-of-range} (-> (store/ledger db) last :basis)))
      (is (empty? (store/shipment-history db))))))

(deftest end-of-line-defect-is-held-and-unoverridable
  (testing "an unresolved end-of-line defect on a cell-batch -> HOLD, and never reaches request-approval -- exercised via :end-of-line-quality/screen DIRECTLY, not via the actuation op against an unscreened cell-batch (see this actor's governor ns docstring / parksafety's ADR-2607071922 Decision 5 / every prior sibling's ADR-0001)"
    (let [[db actor] (fresh)
          res (exec-op actor "t6" {:op :end-of-line-quality/screen :subject "batch-4"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:end-of-line-defect-unresolved} (-> (store/ledger db) first :basis)))
      (is (nil? (store/eol-screen-of db "batch-4")) "no clearance written"))))

(deftest ship-cell-batch-always-escalates-then-human-decides
  (testing "a clean, fully-verified, in-spec cell-batch still ALWAYS interrupts for human approval -- actuation/ship-cell-batch is never auto"
    (let [[db actor] (fresh)
          _ (verify! actor "t7pre" "batch-1")
          _ (simulate-robotics! actor "t7pre2" "batch-1")
          r1 (exec-op actor "t7" {:op :actuation/ship-cell-batch :subject "batch-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, shipment record drafted"
        (let [r2 (approve! actor "t7")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:cell-batch-shipped? (store/cell-batch db "batch-1"))))
          (is (= 1 (count (store/shipment-history db))) "one draft shipment record"))))))

(deftest issue-safety-certificate-always-escalates-then-human-decides
  (testing "a clean, fully-verified, resolved-defect cell-batch still ALWAYS interrupts for human approval -- actuation/issue-safety-certificate is never auto"
    (let [[db actor] (fresh)
          _ (verify! actor "t8pre" "batch-1")
          _ (screen! actor "t8pre2" "batch-1")
          r1 (exec-op actor "t8" {:op :actuation/issue-safety-certificate :subject "batch-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, certificate record drafted"
        (let [r2 (approve! actor "t8")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:safety-certified? (store/cell-batch db "batch-1"))))
          (is (= 1 (count (store/certificate-history db))) "one draft certificate record"))))))

(deftest ship-cell-batch-double-shipment-is-held
  (testing "shipping the same cell-batch's action twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (verify! actor "t9pre" "batch-1")
          _ (simulate-robotics! actor "t9pre2" "batch-1")
          _ (exec-op actor "t9a" {:op :actuation/ship-cell-batch :subject "batch-1"} operator)
          _ (approve! actor "t9a")
          res (exec-op actor "t9" {:op :actuation/ship-cell-batch :subject "batch-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-shipped} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/shipment-history db))) "still only the one earlier shipment"))))

(deftest issue-safety-certificate-double-issuance-is-held
  (testing "issuing the same cell-batch's Battery Safety Test Report twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (verify! actor "t10pre" "batch-1")
          _ (screen! actor "t10pre2" "batch-1")
          _ (exec-op actor "t10a" {:op :actuation/issue-safety-certificate :subject "batch-1"} operator)
          _ (approve! actor "t10a")
          res (exec-op actor "t10" {:op :actuation/issue-safety-certificate :subject "batch-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-certified} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/certificate-history db))) "still only the one earlier certificate issuance"))))

(deftest robotics-simulation-always-needs-approval
  (testing "robotics/simulate-crush-test is never in any phase's :auto set -- always human approval, even when clean"
    (let [[db actor] (fresh)
          res (exec-op actor "t11" {:op :robotics/simulate-crush-test :subject "batch-1"} operator)]
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "t11")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (true? (:robotics-sim-verified? (store/cell-batch db "batch-1"))))))))

(deftest ship-cell-batch-without-robotics-simulation-is-held
  (testing "actuation/ship-cell-batch before the robot UN 38.3 T6 crush-test mission ever ran -> HOLD (robotics-simulation-missing)"
    (let [[db actor] (fresh)
          _ (verify! actor "t12pre" "batch-1")
          res (exec-op actor "t12" {:op :actuation/ship-cell-batch :subject "batch-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:robotics-simulation-missing} (-> (store/ledger db) last :basis)))
      (is (empty? (store/shipment-history db))))))

(deftest robotics-simulation-out-of-tolerance-is-held
  (testing "batch-5 has a robotics-sim already on file, but its own REAL physics-2d-simulated crush-force reading exceeds UN 38.3 T6's 13kN ceiling on INDEPENDENT recheck -> HOLD, never trusts the on-file verdict alone"
    (let [[db actor] (fresh)
          _ (verify! actor "t13pre" "batch-5")
          res (exec-op actor "t13" {:op :actuation/ship-cell-batch :subject "batch-5"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:robotics-simulation-out-of-tolerance} (-> (store/ledger db) last :basis)))
      (is (empty? (store/shipment-history db))))))

(deftest every-decision-leaves-one-ledger-fact
  (testing "write-only-through-ledger: N operations -> N ledger facts"
    (let [[db actor] (fresh)]
      (exec-op actor "a" {:op :cell-batch/intake :subject "batch-1"
                          :patch {:id "batch-1" :batch-name "Meridian 21700 Cell Batch CB-4401"}} operator)
      (exec-op actor "b" {:op :cell-safety-rules/verify :subject "batch-2"} operator)
      (is (= 2 (count (store/ledger db)))
          "one commit + one hold, both recorded"))))
