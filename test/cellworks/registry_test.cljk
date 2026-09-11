(ns cellworks.registry-test
  (:require [clojure.test :refer [deftest is]]
            [cellworks.registry :as r]))

;; ----------------------------- cell-batch-resistance-out-of-range? -----------------------------

(deftest not-out-of-range-when-within-bounds
  (is (not (r/cell-batch-resistance-out-of-range? {:internal-resistance-deviation-actual-mohm 2.0 :internal-resistance-deviation-min-mohm -5.0 :internal-resistance-deviation-max-mohm 5.0})))
  (is (not (r/cell-batch-resistance-out-of-range? {:internal-resistance-deviation-actual-mohm -5.0 :internal-resistance-deviation-min-mohm -5.0 :internal-resistance-deviation-max-mohm 5.0})))
  (is (not (r/cell-batch-resistance-out-of-range? {:internal-resistance-deviation-actual-mohm 5.0 :internal-resistance-deviation-min-mohm -5.0 :internal-resistance-deviation-max-mohm 5.0}))))

(deftest out-of-range-when-below-minimum-or-above-maximum
  (is (r/cell-batch-resistance-out-of-range? {:internal-resistance-deviation-actual-mohm -6.0 :internal-resistance-deviation-min-mohm -5.0 :internal-resistance-deviation-max-mohm 5.0}))
  (is (r/cell-batch-resistance-out-of-range? {:internal-resistance-deviation-actual-mohm 8.5 :internal-resistance-deviation-min-mohm -5.0 :internal-resistance-deviation-max-mohm 5.0})))

(deftest out-of-range-is-false-on-missing-fields
  (is (not (r/cell-batch-resistance-out-of-range? {})))
  (is (not (r/cell-batch-resistance-out-of-range? {:internal-resistance-deviation-actual-mohm 8.5}))))

;; ----------------------------- register-cell-batch-shipment -----------------------------

(deftest shipment-is-a-draft-not-a-real-shipment
  (let [result (r/register-cell-batch-shipment "batch-1" "UN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest shipment-assigns-shipment-number
  (let [result (r/register-cell-batch-shipment "batch-1" "UN" 7)]
    (is (= (get result "shipment_number") "UN-CBS-000007"))
    (is (= (get-in result ["record" "cell_batch_id"]) "batch-1"))
    (is (= (get-in result ["record" "kind"]) "cell-batch-shipment-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest shipment-validation-rules
  (is (thrown? Exception (r/register-cell-batch-shipment "" "UN" 0)))
  (is (thrown? Exception (r/register-cell-batch-shipment "batch-1" "" 0)))
  (is (thrown? Exception (r/register-cell-batch-shipment "batch-1" "UN" -1))))

;; ----------------------------- register-safety-certificate -----------------------------

(deftest certificate-is-a-draft-not-real-certification
  (let [result (r/register-safety-certificate "batch-1" "UN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest certificate-assigns-certificate-number
  (let [result (r/register-safety-certificate "batch-1" "UN" 3)]
    (is (= (get result "certificate_number") "UN-BSTR-000003"))
    (is (= (get-in result ["record" "cell_batch_id"]) "batch-1"))
    (is (= (get-in result ["record" "kind"]) "safety-certificate-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest certificate-validation-rules
  (is (thrown? Exception (r/register-safety-certificate "" "UN" 0)))
  (is (thrown? Exception (r/register-safety-certificate "batch-1" "" 0)))
  (is (thrown? Exception (r/register-safety-certificate "batch-1" "UN" -1))))

(deftest history-is-append-only
  (let [c1 (r/register-cell-batch-shipment "batch-1" "UN" 0)
        hist (r/append [] c1)
        c2 (r/register-cell-batch-shipment "batch-2" "UN" 1)
        hist2 (r/append hist c2)]
    (is (= 2 (count hist2)))
    (is (= "UN-CBS-000000" (get-in hist2 [0 "record_id"])))
    (is (= "UN-CBS-000001" (get-in hist2 [1 "record_id"])))))
