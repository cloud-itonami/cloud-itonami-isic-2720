(ns cellworks.store-contract-test
  "The Store contract, run against BOTH backends. Proving MemStore and
  the Datomic-backed (langchain.db) store satisfy the same contract is
  what makes 'swap the SSoT for Datomic / kotoba-server' a
  configuration change, not a rewrite -- see `cloud-itonami-isic-6511`'s
  `underwriting.store-contract-test` for the same pattern on the
  sibling actor."
  (:require [clojure.test :refer [deftest is testing]]
            [cellworks.robotics :as robotics]
            [cellworks.store :as store]))

(defn- backends []
  [["MemStore" (store/seed-db)] ["DatomicStore" (store/datomic-seed-db)]])

(deftest read-parity
  (doseq [[label s] (backends)]
    (testing label
      (is (= "Meridian 21700 Cell Batch CB-4401" (:batch-name (store/cell-batch s "batch-1"))))
      (is (= "UN" (:jurisdiction (store/cell-batch s "batch-1"))))
      (is (= 2.0 (:internal-resistance-deviation-actual-mohm (store/cell-batch s "batch-1"))))
      (is (= -5.0 (:internal-resistance-deviation-min-mohm (store/cell-batch s "batch-1"))))
      (is (= 5.0 (:internal-resistance-deviation-max-mohm (store/cell-batch s "batch-1"))))
      (is (false? (:cell-batch-defect-unresolved? (store/cell-batch s "batch-1"))))
      (is (= 8.5 (:internal-resistance-deviation-actual-mohm (store/cell-batch s "batch-3"))))
      (is (true? (:cell-batch-defect-unresolved? (store/cell-batch s "batch-4"))))
      (is (false? (:robotics-sim-verified? (store/cell-batch s "batch-1"))) "no robotics mission has run yet")
      (is (true? (:robotics-sim-verified? (store/cell-batch s "batch-5"))) "seeded as already-on-file")
      (is (= 300 (:crush-press-platen-mass-kg (store/cell-batch s "batch-5"))))
      (is (> (:sim-peak-crush-force-n (store/cell-batch s "batch-5"))
             robotics/un383-t6-crush-force-ceiling-n)
          "batch-5's real physics-2d-simulated crush force exceeds UN 38.3 T6's 13kN ceiling")
      (is (< (:sim-peak-crush-force-n (store/cell-batch s "batch-1"))
             robotics/un383-t6-crush-force-ceiling-n)
          "batch-1's real physics-2d-simulated crush force clears UN 38.3 T6's 13kN ceiling")
      (is (= 7619.047619047618 (:sim-peak-crush-force-n (store/cell-batch s "batch-1"))))
      (is (= 28571.42857142857 (:sim-peak-crush-force-n (store/cell-batch s "batch-5"))))
      (is (false? (:cell-batch-shipped? (store/cell-batch s "batch-1"))))
      (is (false? (:safety-certified? (store/cell-batch s "batch-1"))))
      (is (= ["batch-1" "batch-2" "batch-3" "batch-4" "batch-5"]
             (mapv :id (store/all-cell-batches s))))
      (is (nil? (store/eol-screen-of s "batch-1")))
      (is (nil? (store/cell-safety-verification-of s "batch-1")))
      (is (= [] (store/ledger s)))
      (is (= [] (store/shipment-history s)))
      (is (= [] (store/certificate-history s)))
      (is (zero? (store/next-shipment-sequence s "UN")))
      (is (zero? (store/next-certificate-sequence s "UN")))
      (is (false? (store/cell-batch-already-shipped? s "batch-1")))
      (is (false? (store/cell-batch-already-certified? s "batch-1"))))))

(deftest write-and-ledger-parity
  (doseq [[label s] (backends)]
    (testing label
      (testing "partial upsert merges, preserving untouched fields"
        (store/commit-record! s {:effect :cell-batch/upsert
                                 :value {:id "batch-1" :batch-name "Meridian 21700 Cell Batch CB-4401"}})
        (is (= "Meridian 21700 Cell Batch CB-4401" (:batch-name (store/cell-batch s "batch-1"))))
        (is (= "UN" (:jurisdiction (store/cell-batch s "batch-1"))) "unrelated field preserved"))
      (testing "robotics-sim result commits via :cell-batch/upsert and reads back"
        (store/commit-record! s {:effect :cell-batch/upsert
                                 :value {:id "batch-1" :robotics-sim-verified? true
                                        :robotics-sim-record {:mission-id "m-1" :passed? true}}})
        (is (true? (:robotics-sim-verified? (store/cell-batch s "batch-1"))))
        (is (= {:mission-id "m-1" :passed? true} (:robotics-sim-record (store/cell-batch s "batch-1"))))
        (is (= "UN" (:jurisdiction (store/cell-batch s "batch-1"))) "unrelated field still preserved"))
      (testing "verification / eol-screen payloads commit and read back"
        (store/commit-record! s {:effect :cell-safety-verification/set :path ["batch-1"]
                                 :payload {:jurisdiction "UN" :checklist ["a" "b"]}})
        (is (= {:jurisdiction "UN" :checklist ["a" "b"]} (store/cell-safety-verification-of s "batch-1")))
        (store/commit-record! s {:effect :eol-screen/set :path ["batch-1"]
                                 :payload {:cell-batch-id "batch-1" :verdict :resolved}})
        (is (= {:cell-batch-id "batch-1" :verdict :resolved} (store/eol-screen-of s "batch-1"))))
      (testing "cell-batch shipment drafts a record and advances the sequence"
        (store/commit-record! s {:effect :cell-batch/mark-shipped :path ["batch-1"]})
        (is (= "UN-CBS-000000" (get (first (store/shipment-history s)) "record_id")))
        (is (= "cell-batch-shipment-draft" (get (first (store/shipment-history s)) "kind")))
        (is (true? (:cell-batch-shipped? (store/cell-batch s "batch-1"))))
        (is (= 1 (count (store/shipment-history s))))
        (is (= 1 (store/next-shipment-sequence s "UN")))
        (is (true? (store/cell-batch-already-shipped? s "batch-1")))
        (is (false? (store/cell-batch-already-shipped? s "batch-2"))))
      (testing "Battery Safety Test Report drafts a record and advances the sequence"
        (store/commit-record! s {:effect :cell-batch/mark-certified :path ["batch-1"]})
        (is (= "UN-BSTR-000000" (get (first (store/certificate-history s)) "record_id")))
        (is (= "safety-certificate-draft" (get (first (store/certificate-history s)) "kind")))
        (is (true? (:safety-certified? (store/cell-batch s "batch-1"))))
        (is (= 1 (count (store/certificate-history s))))
        (is (= 1 (store/next-certificate-sequence s "UN")))
        (is (true? (store/cell-batch-already-certified? s "batch-1")))
        (is (false? (store/cell-batch-already-certified? s "batch-2"))))
      (testing "ledger is append-only and order-preserving"
        (store/append-ledger! s {:op :a :disposition :commit})
        (store/append-ledger! s {:op :b :disposition :hold})
        (is (= [:commit :hold] (mapv :disposition (store/ledger s))))))))

(deftest datomic-empty-store-is-usable
  (let [s (store/datomic-store)]
    (is (nil? (store/cell-batch s "nope")))
    (is (= [] (store/all-cell-batches s)))
    (is (= [] (store/ledger s)))
    (is (= [] (store/shipment-history s)))
    (is (= [] (store/certificate-history s)))
    (is (zero? (store/next-shipment-sequence s "UN")))
    (is (zero? (store/next-certificate-sequence s "UN")))
    (store/with-cell-batches s {"x" {:id "x" :batch-name "n"
                                     :internal-resistance-deviation-actual-mohm 2.0
                                     :internal-resistance-deviation-min-mohm -5.0
                                     :internal-resistance-deviation-max-mohm 5.0
                                     :cell-batch-defect-unresolved? false
                                     :cell-batch-shipped? false :safety-certified? false
                                     :jurisdiction "UN" :status :intake}})
    (is (= "n" (:batch-name (store/cell-batch s "x"))))))
