(ns cellworks.pem-stack
  "pem_stack.cljc — PEM stack assembly-and-test cell decision contract
  (:pem-stack-assembly-and-test manufacturing cell,
  scripts/hermes-magnesium-systems-bots/system-scope.edn on
  com-junkawasaki origin/main).

  First executable slice for the PEM fuel-cell stack cell: a PURE decision
  layer that models activity -> decision -> effect -> audit for leak/pressure,
  open-circuit-voltage and hi-pot dielectric test activity on an assembled
  stack, plus procurement screening for the cell's equipment classes. The bot
  may design and simulate; it may NOT command physical equipment — a test-rig
  command is refused unconditionally.

  First-generation boundary: MEA manufacture is OUTSOURCED. An assembly plan
  therefore requires outsourced MEA lots with disclosed provenance and lot
  traceability; this module never plans in-house MEA fabrication.

  Hazard boundaries encoded (hydrogen pressure/leaks, high voltage):
    - calibrated hydrogen leak detection must be declared before any
      pressurized test is planned
    - an inert (N2) purge procedure must be verified for the rig before
      hydrogen-bearing activity
    - high-voltage lockout/tagout and verified pressure relief are required
      interlocks (hi-pot + pressure decay in one plan)
    - human approval is required for every hazardous step (pressurize /
      hv-test) — absence defers, never approves
    - no test threshold is invented: measured leak rate, measured OCV and
      measured hi-pot leakage are required inputs; missing price / lead-time /
      utility / safety / compliance values are recorded as :unmeasured

  Pure fns; deterministic; keyword-keyed records; stdlib only."
  (:require [clojure.set :as set]
            [kotoba.lang.text :as str]))

;; ── constants ──────────────────────────────────────────────────────────────

(def ^:private required-interlocks
  #{:h2-detector-calibrated :n2-purge-verified :hv-lockout-tagout :pressure-relief-verified})
(def ^:private required-hazardous-steps #{:pressurize :hv-test})
(def ^:private recognized-conditions #{"new" "used" "refurbished" "unknown"})
(def ^:private recognized-equipment-classes
  #{:helium-leak-and-pressure-test :pem-stack-assembly-and-test :smt-and-power-electronics-test})

;; ── helpers ────────────────────────────────────────────────────────────────

(defn- present? [x]
  (cond (string? x) (not (str/blank? x))
        (nil? x) false
        :else true))

(defn- audit-record
  "The audit tail every decision returns: what was decided, which gates were
  checked, and the explicit no-physical-command attestation."
  [activity-id decision refusal gates-checked effect]
  {:audit/activity-id activity-id
   :audit/decision decision
   :audit/refusal refusal
   :audit/gates-checked gates-checked
   :audit/effect effect
   :audit/bot-commanded-equipment false})

(defn- refuse [activity-id refusal gates]
  {:decision :refused
   :effect {:effect/kind :none}
   :audit (audit-record activity-id :refused refusal gates {:effect/kind :none})})

;; ── activity 1: stack assembly-and-test plan (hazardous — human approval) ──

(defn plan-stack-assembly-and-test
  "One PEM stack assembly-and-test activity.

  `req` keys (all measured values must be supplied by the caller; this function
  invents none — no pass/fail threshold, capacity or yield is assumed here):
    :activity/id            string
    :stack/id               string — the assembled stack under test
    :mea-lots               vector of ≥1 {:lot-id string :supplier string
                              :provenance-disclosed? boolean} — MEA manufacture
                              is outsourced; in-house MEA fabrication is out of
                              boundary and is never planned
    :measured-leak-rate     {:value number  :unit string} — measured on the rig
    :measured-ocv-v         number — measured stack open-circuit voltage
    :measured-hipot-leak-ua number — measured hi-pot leakage current
    :interlocks             collection of interlock keywords (calibrated H2
                            detector, verified N2 purge, HV lockout/tagout,
                            verified pressure relief)
    :witness-robot-dids     vector of ≥2 robot DIDs (witness quorum per record)
    :human-approval         {:approver-did string  :approved-at string
                             :scope #{:pressurize :hv-test} — must cover both
                             hazardous steps}
    :requested-effect       :simulate-test-plan (the only admissible kind) or
                            :command-test-rig (refused unconditionally)

  Returns {:decision :approved|:refused :effect {...} :audit {...}}."
  [req]
  (let [activity-id (get req :activity/id "")
        gates (atom [])
        note (fn [g] (swap! gates conj g))
        mea-lots (get req :mea-lots)
        mea-lots-ok?
        (and (vector? mea-lots)
             (pos? (count mea-lots))
             (every? (fn [l]
                       (and (map? l)
                            (present? (get l :lot-id))
                            (present? (get l :supplier))
                            (true? (get l :provenance-disclosed?))))
                     mea-lots))
        ;; ordered checks; first failure refuses
        refusal
        (cond
          (not (present? activity-id))
          (do (note :activity-id-present)
              "activity-id: a stack assembly-and-test activity needs an :activity/id")

          (not (and (present? (get req :stack/id)) (string? (get req :stack/id))))
          (do (note :stack-id-present)
              "stack-id: the activity must name the :stack/id under test")

          (not mea-lots-ok?)
          (do (note :mea-lots-outsourced-traceable)
              "boundary: MEA manufacture is outsourced — the plan requires ≥1 disclosed MEA lot (lot-id, supplier, provenance-disclosed?) and never plans in-house MEA fabrication")

          (not (and (map? (get req :measured-leak-rate))
                    (number? (get-in req [:measured-leak-rate :value]))
                    (present? (get-in req [:measured-leak-rate :unit]))))
          (do (note :measured-leak-rate-required)
              "unmeasured: :measured-leak-rate {value unit} is required from the rig; this module never substitutes a datasheet constant or an assumed pass threshold")

          (not (number? (get req :measured-ocv-v)))
          (do (note :measured-ocv-required)
              "unmeasured: :measured-ocv-v is required and must be measured on the assembled stack")

          (not (number? (get req :measured-hipot-leak-ua)))
          (do (note :measured-hipot-required)
              "unmeasured: :measured-hipot-leak-ua is required and must be measured by the hi-pot tester")

          (not (set/subset? required-interlocks
                            (set (map keyword (get req :interlocks)))))
          (do (note :interlocks-complete)
              (str "safety: interlocks incomplete; required "
                   (pr-str (sort required-interlocks))
                   " got " (pr-str (sort (set (map keyword (get req :interlocks)))))))

          (not (>= (count (remove str/blank? (map str (get req :witness-robot-dids))))
                   2))
          (do (note :witness-quorum)
              "witness-quorum: the test record needs ≥ 2 robot witness signers")

          (= :command-test-rig (get req :requested-effect))
          (do (note :no-physical-command)
              "no-physical-command: the bot may design and simulate but may not command physical equipment; only :simulate-test-plan is admissible")

          (not (and (map? (get req :human-approval))
                    (present? (get-in req [:human-approval :approver-did]))
                    (present? (get-in req [:human-approval :approved-at]))
                    (set/subset? required-hazardous-steps
                                 (set (map keyword (get-in req [:human-approval :scope]))))))
          (do (note :human-approval-required)
              (str "human-approval: pressurization and hi-pot are hazardous operations; a named human approver with "
                   (pr-str (sort required-hazardous-steps))
                   " scope must be recorded — absence defers, never approves"))

          :else nil)]
    (if refusal
      (refuse activity-id refusal @gates)
      (let [effect {:effect/kind :simulate-test-plan-only
                    :effect/machine-command false
                    :effect/plan {:stack/id (:stack/id req)
                                  :mea-lots (vec (:mea-lots req))
                                  :measured-leak-rate (:measured-leak-rate req)
                                  :measured-ocv-v (:measured-ocv-v req)
                                  :measured-hipot-leak-ua (:measured-hipot-leak-ua req)
                                  :interlocks (sort (set (map keyword (:interlocks req))))
                                  :witness-robot-dids (vec (:witness-robot-dids req))
                                  :mea-manufactured-in-house false}}]
        {:decision :approved
         :effect effect
         :audit (audit-record activity-id :approved "" (conj @gates :human-approval-approved :no-physical-command) effect)}))))

;; ── activity 2: equipment-offer screening (procurement — always deferred) ──

(defn screen-equipment-offer
  "Screen one equipment offer for the PEM stack assembly-and-test cell
  (e.g. :helium-leak-and-pressure-test or :pem-stack-assembly-and-test
  equipment class). Procurement is a financial commitment: the decision is
  ALWAYS :deferred to a human approver; this fn only assembles the auditable
  evidence record. Condition must be distinguished as 'new', 'used',
  'refurbished' or 'unknown'. Missing price / lead-time / utility /
  safety / compliance values are recorded as :unmeasured — never invented."
  [offer]
  (let [activity-id (get offer :activity/id "")
        gates (atom [])
        condition (get offer :condition)
        source-url (get offer :source-url)
        equipment-class (some-> (get offer :equipment-class) keyword)]
    (swap! gates conj :condition-distinguished :source-recorded)
    (cond
      (not (present? activity-id))
      (refuse activity-id "activity-id: an equipment screening needs an :activity/id" @gates)

      (not (contains? recognized-equipment-classes equipment-class))
      (refuse activity-id
              (str "equipment-class: must be one of "
                   (pr-str (sort (map name recognized-equipment-classes)))
                   "; got " (pr-str (get offer :equipment-class)))
              @gates)

      (not (contains? recognized-conditions condition))
      (refuse activity-id
              (str "condition: must be distinguished as one of "
                   (pr-str (sort recognized-conditions)) "; got " (pr-str condition))
              @gates)

      (not (present? source-url))
      (refuse activity-id "source: an offer needs a first-party :source-url" @gates)

      :else
      (let [effect {:effect/kind :deferred-human-approval
                    :effect/machine-command false
                    :effect/screening
                    {:manufacturer (get offer :manufacturer)
                     :model (get offer :model)
                     :equipment-class equipment-class
                     :condition condition
                     :seller (get offer :seller)
                     :source-url source-url
                     :observed-at (get offer :observed-at)
                     :unmeasured (dissoc (select-keys offer [:price :currency :lead-time
                                                             :utility :safety :compliance])
                                         nil)
                     :unmeasured-fields [:price :currency :lead-time :utility :safety :compliance]}}]
        {:decision :deferred
         :effect effect
         :audit (audit-record activity-id :deferred
                              "procurement is a human decision; screening evidence assembled only"
                              (conj @gates :human-approval-required :no-financial-commitment)
                              effect)}))))
