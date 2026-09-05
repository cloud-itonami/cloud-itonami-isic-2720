(ns cellworks.pem-stack-test
  "Focused tests for the PEM stack assembly-and-test cell decision contract
  (activity -> decision -> effect -> audit). Pure; deterministic; stdlib only."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is run-tests]]
            [cellworks.pem-stack :as pem]))

(def ^:private valid-req
  {:activity/id "act-pem-0001"
   :stack/id "stack-2026-0001"
   :mea-lots [{:lot-id "MEA-LOT-88" :supplier "outsourced-mea-vendor"
               :provenance-disclosed? true}]
   :measured-leak-rate {:value 0.8 :unit "sccm"}
   :measured-ocv-v 48.2
   :measured-hipot-leak-ua 12
   :interlocks [:h2-detector-calibrated :n2-purge-verified
                :hv-lockout-tagout :pressure-relief-verified]
   :witness-robot-dids ["did:web:etzhayyim.com:itonami:otete"
                        "did:web:etzhayyim.com:itonami:mimi"]
   :human-approval {:approver-did "did:web:etzhayyim.com:person:owner"
                    :approved-at "2026-08-31T00:00:00Z"
                    :scope #{:pressurize :hv-test}}
   :requested-effect :simulate-test-plan})

(def ^:private valid-offer
  {:activity/id "act-pem-eq-0001"
   :equipment-class :helium-leak-and-pressure-test
   :manufacturer "Example Leak Test Instruments"
   :model "ELT-100"
   :condition "used"
   :seller "Example owner-operated dealer"
   :source-url "https://example-leak-instruments.example/inventory/elt-100"
   :observed-at "2026-08-31T00:00:00Z"})

;; ── plan-stack-assembly-and-test ───────────────────────────────────────────

(deftest test-happy-path-approves-simulate-only
  (let [r (pem/plan-stack-assembly-and-test valid-req)]
    (is (= :approved (:decision r)))
    (is (= :simulate-test-plan-only (get-in r [:effect :effect/kind])))
    (is (false? (get-in r [:effect :effect/machine-command])))
    (is (false? (get-in r [:effect :effect/plan :mea-manufactured-in-house])))
    (is (false? (get-in r [:audit :audit/bot-commanded-equipment])))
    (is (contains? (set (:audit/gates-checked (:audit r))) :human-approval-approved))))

(deftest test-rig-command-refused-unconditionally
  (let [r (pem/plan-stack-assembly-and-test (assoc valid-req :requested-effect :command-test-rig))]
    (is (= :refused (:decision r)))
    (is (str/includes? (:audit/refusal (:audit r)) "no-physical-command"))))

(deftest test-missing-mea-lots-refused-outsourced-boundary
  (let [r (pem/plan-stack-assembly-and-test (dissoc valid-req :mea-lots))]
    (is (= :refused (:decision r)))
    (is (str/includes? (:audit/refusal (:audit r)) "outsourced"))))

(deftest test-mea-lot-undisclosed-provenance-refused
  (let [r (pem/plan-stack-assembly-and-test
           (assoc-in valid-req [:mea-lots 0 :provenance-disclosed?] false))]
    (is (= :refused (:decision r)))
    (is (str/includes? (:audit/refusal (:audit r)) "outsourced"))))

(deftest test-missing-leak-rate-refused-not-invented
  (let [r (pem/plan-stack-assembly-and-test (dissoc valid-req :measured-leak-rate))]
    (is (= :refused (:decision r)))
    (is (str/includes? (:audit/refusal (:audit r)) "unmeasured"))
    (is (str/includes? (:audit/refusal (:audit r)) "never substitutes"))))

(deftest test-missing-ocv-refused
  (let [r (pem/plan-stack-assembly-and-test (dissoc valid-req :measured-ocv-v))]
    (is (= :refused (:decision r)))
    (is (str/includes? (:audit/refusal (:audit r)) "unmeasured"))))

(deftest test-missing-hipot-refused
  (let [r (pem/plan-stack-assembly-and-test (dissoc valid-req :measured-hipot-leak-ua))]
    (is (= :refused (:decision r)))
    (is (str/includes? (:audit/refusal (:audit r)) "unmeasured"))))

(deftest test-missing-interlocks-refused-with-required-list
  (let [r (pem/plan-stack-assembly-and-test
           (assoc valid-req :interlocks [:h2-detector-calibrated]))]
    (is (= :refused (:decision r)))
    (is (str/includes? (:audit/refusal (:audit r)) "n2-purge-verified")))
  (let [r (pem/plan-stack-assembly-and-test
           (assoc valid-req :interlocks [:h2-detector-calibrated :n2-purge-verified
                                         :pressure-relief-verified]))]
    (is (= :refused (:decision r)))
    (is (str/includes? (:audit/refusal (:audit r)) "hv-lockout-tagout"))))

(deftest test-witness-quorum-refused
  (let [r (pem/plan-stack-assembly-and-test (assoc valid-req :witness-robot-dids ["did:one"]))]
    (is (= :refused (:decision r)))
    (is (str/includes? (:audit/refusal (:audit r)) "witness-quorum"))))

(deftest test-missing-human-approval-defers-never-approves
  (let [r (pem/plan-stack-assembly-and-test (dissoc valid-req :human-approval))]
    (is (= :refused (:decision r)))
    (is (str/includes? (:audit/refusal (:audit r)) "human-approval")))
  (let [r (pem/plan-stack-assembly-and-test
           (assoc-in valid-req [:human-approval :scope] #{:pressurize}))]
    (is (= :refused (:decision r)))
    (is (str/includes? (:audit/refusal (:audit r)) "human-approval"))))

(deftest test-blank-activity-id-refused
  (let [r (pem/plan-stack-assembly-and-test (assoc valid-req :activity/id ""))]
    (is (= :refused (:decision r)))
    (is (str/includes? (:audit/refusal (:audit r)) "activity-id"))))

(deftest test-approved-plan-preserves-measured-values-only
  (let [r (pem/plan-stack-assembly-and-test valid-req)
        plan (get-in r [:effect :effect/plan])]
    (is (= {:value 0.8 :unit "sccm"} (:measured-leak-rate plan)))
    (is (= 48.2 (:measured-ocv-v plan)))
    (is (= [:h2-detector-calibrated :hv-lockout-tagout
            :n2-purge-verified :pressure-relief-verified] (:interlocks plan)))
    (is (nil? (:pass-threshold plan)))))

;; ── screen-equipment-offer ─────────────────────────────────────────────────

(deftest test-offer-always-deferred-to-human
  (let [r (pem/screen-equipment-offer valid-offer)]
    (is (= :deferred (:decision r)))
    (is (= :deferred-human-approval (get-in r [:effect :effect/kind])))
    (is (= "used" (get-in r [:effect :effect/screening :condition])))
    (is (false? (get-in r [:audit :audit/bot-commanded-equipment])))
    (is (contains? (set (:audit/gates-checked (:audit r))) :no-financial-commitment))))

(deftest test-offer-condition-must-be-distinguished
  (let [r (pem/screen-equipment-offer (assoc valid-offer :condition "bargain"))]
    (is (= :refused (:decision r)))
    (is (str/includes? (:audit/refusal (:audit r)) "condition"))))

(deftest test-offer-equipment-class-restricted
  (let [r (pem/screen-equipment-offer (assoc valid-offer :equipment-class :injection-molding))]
    (is (= :refused (:decision r)))
    (is (str/includes? (:audit/refusal (:audit r)) "equipment-class"))))

(deftest test-offer-requires-first-party-source
  (let [r (pem/screen-equipment-offer (dissoc valid-offer :source-url))]
    (is (= :refused (:decision r)))
    (is (str/includes? (:audit/refusal (:audit r)) "first-party"))))

(deftest test-offer-records-missing-values-as-unmeasured
  (let [r (pem/screen-equipment-offer valid-offer)
        screening (get-in r [:effect :effect/screening])]
    (is (empty? (:unmeasured screening)))
    (is (= [:price :currency :lead-time :utility :safety :compliance]
           (:unmeasured-fields screening)))))

(deftest test-offer-with-price-keeps-it-unmeasured-tagged
  (let [r (pem/screen-equipment-offer (assoc valid-offer :price 12000 :currency "JPY"))
        screening (get-in r [:effect :effect/screening])]
    (is (= 12000 (get-in screening [:unmeasured :price])))
    (is (= "JPY" (get-in screening [:unmeasured :currency])))
    (is (= :deferred (:decision r)))))

;; ── runner ─────────────────────────────────────────────────────────────────

(defn -main [& _]
  (let [{:keys [fail error]} (run-tests 'cellworks.pem-stack-test)]
    #?(:clj (System/exit (if (zero? (+ fail error)) 0 1))
       :cljs nil)))
