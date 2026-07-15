(ns cellworks.export-test
  "Audit-package export contract -- social/regulatory hand-off shape."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [langgraph.graph :as g]
            [cellworks.export :as export]
            [cellworks.operation :as op]
            [cellworks.store :as store]))

(def operator {:actor-id "op-1" :actor-role :quality-engineer :phase 3})

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context operator} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}}
          {:thread-id tid :resume? true}))

(defn- seed-with-one-shipment []
  (let [db (store/seed-db)
        actor (op/build db)]
    (exec! actor "v" {:op :cell-safety-rules/verify :subject "batch-1"})
    (approve! actor "v")
    (exec! actor "r" {:op :robotics/simulate-crush-test :subject "batch-1"})
    (approve! actor "r")
    (exec! actor "d" {:op :actuation/ship-cell-batch :subject "batch-1"})
    (approve! actor "d")
    db))

(deftest audit-package-shape
  (let [db (seed-with-one-shipment)
        pkg (export/audit-package db)]
    (is (= "2720" (:isic pkg)))
    (is (= "cloud-itonami-isic-2720" (:business-id pkg)))
    (is (= :edn-maps (:format pkg)))
    (is (pos? (get-in pkg [:counts :ledger])))
    (is (= 1 (get-in pkg [:counts :shipments])))
    (is (some #(= "batch-1" (:id %)) (:cell-batches pkg)))
    (is (true? (:cell-batch-shipped?
                (first (filter #(= "batch-1" (:id %)) (:cell-batches pkg))))))))

(deftest csv-bundle-has-headers-and-rows
  (let [db (seed-with-one-shipment)
        bundle (export/package->csv-bundle db)]
    (is (every? bundle ["cell-batches.csv" "ledger.csv" "shipments.csv" "safety-certificates.csv"]))
    (is (str/starts-with? (get bundle "cell-batches.csv") "id,batch-name,"))
    (is (re-find #"batch-1" (get bundle "cell-batches.csv")))
    (is (re-find #"UN-CBS-000000" (get bundle "shipments.csv")))
    (is (re-find #":actuation/ship-cell-batch" (get bundle "ledger.csv")))))

(deftest empty-store-export-is-usable
  (let [db (store/seed-db)
        pkg (export/audit-package db)
        bundle (export/package->csv-bundle db)]
    (is (= 0 (get-in pkg [:counts :shipments])))
    (is (= 5 (get-in pkg [:counts :cell-batches])))
    (is (str/includes? (get bundle "ledger.csv") "seq,t,op"))))
