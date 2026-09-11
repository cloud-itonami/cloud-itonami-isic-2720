(ns cellworks.sim
  "Demo driver -- `clojure -M:dev:run`. Walks a clean cell-batch through
  intake -> (an evidence-incomplete shipment attempt) -> cell-safety-
  rules verification -> end-of-line quality screening -> robot UN 38.3
  T6 crush-test mission -> cell-batch-shipment proposal (always
  escalates) -> human approval -> commit, then through Battery Safety
  Test Report proposal (always escalates) -> human approval -> commit,
  then shows every HARD hold this actor defends against (a scheme with
  no spec-basis, an actuation attempted before any cell-safety-rules
  evidence verification, an actuation attempted before the robot
  crush-test mission ever ran, an out-of-spec internal-resistance
  deviation, a robotics mission on file whose independent recheck
  disagrees, an unresolved end-of-line defect screened directly via
  `:end-of-line-quality/screen` [never via an actuation op against an
  unscreened cell-batch -- see this actor's own governor ns docstring /
  the lesson parksafety's ADR-2607071922 Decision 5, and every prior
  sibling's ADR-0001 already recorded, most recently `bodyshop`'s], and
  a double cell-batch-shipment/certificate-issuance of an
  already-processed cell-batch) that never reach a human at all, and
  prints the audit ledger + the draft cell-batch-shipment and
  safety-certificate records."
  (:require [langgraph.graph :as g]
            [cellworks.export :as export]
            [cellworks.store :as store]
            [cellworks.operation :as op]))

(def operator {:actor-id "op-1" :actor-role :quality-engineer :phase 3})

(defn- exec! [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        actor (op/build db)]
    (println "== cell-batch/intake batch-1 (UN, clean; resistance within spec, no defect) ==")
    (println (exec! actor "t1" {:op :cell-batch/intake :subject "batch-1"
                                :patch {:id "batch-1" :batch-name "Meridian 21700 Cell Batch CB-4401"}} operator))

    (println "== actuation/ship-cell-batch batch-1 before any cell-safety-rules evidence verification -> HARD hold (evidence-incomplete) ==")
    (println (exec! actor "t1b" {:op :actuation/ship-cell-batch :subject "batch-1"} operator))

    (println "== cell-safety-rules/verify batch-1 (escalates -- human approves) ==")
    (println (exec! actor "t2" {:op :cell-safety-rules/verify :subject "batch-1"} operator))
    (println (approve! actor "t2"))

    (println "== end-of-line-quality/screen batch-1 (clean; escalates -- human approves) ==")
    (println (exec! actor "t3" {:op :end-of-line-quality/screen :subject "batch-1"} operator))
    (println (approve! actor "t3"))

    (println "== robotics/simulate-crush-test batch-1 (real physics-2d press-platen/cell UN 38.3 T6 crush mission; escalates -- human approves) ==")
    (println (exec! actor "t3b" {:op :robotics/simulate-crush-test :subject "batch-1"} operator))
    (println (approve! actor "t3b"))

    (println "== actuation/ship-cell-batch batch-1 (always escalates -- actuation/ship-cell-batch) ==")
    (let [r (exec! actor "t4" {:op :actuation/ship-cell-batch :subject "batch-1"} operator)]
      (println r)
      (println "-- human quality engineer approves --")
      (println (approve! actor "t4")))

    (println "== actuation/issue-safety-certificate batch-1 (always escalates -- actuation/issue-safety-certificate) ==")
    (let [r (exec! actor "t5" {:op :actuation/issue-safety-certificate :subject "batch-1"} operator)]
      (println r)
      (println "-- human quality engineer approves --")
      (println (approve! actor "t5")))

    (println "== cell-safety-rules/verify batch-2 (ATL, no spec-basis -> HARD hold) ==")
    (println (exec! actor "t6" {:op :cell-safety-rules/verify :subject "batch-2"} operator))

    (println "== cell-safety-rules/verify batch-3 (escalates -- human approves; sets up the out-of-spec resistance test) ==")
    (println (exec! actor "t7" {:op :cell-safety-rules/verify :subject "batch-3"} operator))
    (println (approve! actor "t7"))

    (println "== actuation/ship-cell-batch batch-3 before robotics -> HARD hold (robotics-simulation-missing) ==")
    (println (exec! actor "t7b" {:op :actuation/ship-cell-batch :subject "batch-3"} operator))

    (println "== robotics/simulate-crush-test batch-3 (real physics-2d simulation clears the UN 38.3 T6 ceiling; escalates -- human approves) ==")
    (println (exec! actor "t7c" {:op :robotics/simulate-crush-test :subject "batch-3"} operator))
    (println (approve! actor "t7c"))

    (println "== actuation/ship-cell-batch batch-3 (internal-resistance-deviation 8.5mOhm outside [-5.0,5.0]mOhm bounds -> HARD hold) ==")
    (println (exec! actor "t8" {:op :actuation/ship-cell-batch :subject "batch-3"} operator))

    (println "== actuation/ship-cell-batch batch-5 (robotics-sim on file, but real physics-2d-simulated crush force exceeds the UN 38.3 T6 ceiling on independent recheck -> HARD hold) ==")
    (println (exec! actor "t8b" {:op :cell-safety-rules/verify :subject "batch-5"} operator))
    (println (approve! actor "t8b"))
    (println (exec! actor "t8c" {:op :actuation/ship-cell-batch :subject "batch-5"} operator))

    (println "== end-of-line-quality/screen batch-4 (unresolved -> HARD hold, never reaches a human) ==")
    (println (exec! actor "t9" {:op :end-of-line-quality/screen :subject "batch-4"} operator))

    (println "== actuation/ship-cell-batch batch-1 AGAIN (double-shipment -> HARD hold) ==")
    (println (exec! actor "t10" {:op :actuation/ship-cell-batch :subject "batch-1"} operator))

    (println "== actuation/issue-safety-certificate batch-1 AGAIN (double-issuance -> HARD hold) ==")
    (println (exec! actor "t11" {:op :actuation/issue-safety-certificate :subject "batch-1"} operator))

    (println "== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "== draft cell-batch-shipment records ==")
    (doseq [r (store/shipment-history db)] (println r))

    (println "== draft safety-certificate records ==")
    (doseq [r (store/certificate-history db)] (println r))

    (println "== social hand-off: audit package counts ==")
    (println (:counts (export/audit-package db)))
    (println "== social hand-off: CSV bundle keys ==")
    (println (keys (export/package->csv-bundle db)))))
