(ns cellworks.registry
  "Pure-function cell-batch-shipment + Battery Safety Test Report record
  construction -- an append-only battery-plant book-of-record draft.

  Like every sibling actor's registry, there is no single international
  check-digit standard for a cell-batch-shipment or Battery Safety Test
  Report reference number -- every plant/scheme assigns its own
  reference format. This namespace does NOT invent one; it builds a
  jurisdiction/scheme-scoped sequence number and validates the record's
  required fields, the same honest, non-fabricating discipline
  `cellworks.facts` uses.

  `cell-batch-resistance-out-of-range?` continues this fleet's two-sided
  range check family (`testlab.registry/within-tolerance?` established
  the first, `conservation.registry/body-condition-out-of-range?` the
  second, `water.registry/contaminant-level-out-of-range?` the third,
  `steelworks.registry/heat-chemistry-out-of-range?`/`turbine.registry/
  unit-tolerance-out-of-range?`/`automotive.registry/vehicle-emissions-
  out-of-range?`/`autoparts.registry/part-lot-dppm-out-of-range?`/
  `bodyshop.registry/body-shell-dimension-out-of-range?` further
  siblings), applying the SAME lo/hi bounds-comparison shape to a
  cell-batch's own measured end-of-line internal-resistance deviation
  against the cell-batch's own recorded acceptance-band bounds -- a
  real end-of-line electrical-QA metric (DC-IR/EIS internal-resistance
  measurement against the cell's own rated spec), distinct from
  `cellworks.robotics`'s own crush-force ceiling check (a physics-
  derived mechanical-abuse reading, not an electrical end-of-line
  measurement).

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real plant/MES control system. It builds the RECORD a
  battery plant would keep, not the act of shipping the cell-batch
  robot action or issuing the Battery Safety Test Report itself (that
  is `cellworks.operation`'s `:actuation/ship-cell-batch`/`:actuation/
  issue-safety-certificate`, always human-gated -- see README
  `Actuation`)."
  (:require [clojure.string :as str]))

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is
  the battery-plant's own act, not this actor's. See README
  `Actuation`."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

(defn cell-batch-resistance-out-of-range?
  "Does `cell-batch`'s own `:internal-resistance-deviation-actual-mohm`
  fall outside its own `[:internal-resistance-deviation-min-mohm
  :internal-resistance-deviation-max-mohm]` recorded acceptance-band
  bounds? A pure ground-truth check against the cell-batch's own
  permanent fields -- no upstream comparison needed, and no physics
  re-simulation needed (distinct from `cellworks.robotics`'s own
  crush-force ground-truth check). A further sibling in this fleet's
  two-sided range check family (see ns docstring)."
  [{:keys [internal-resistance-deviation-actual-mohm
           internal-resistance-deviation-min-mohm
           internal-resistance-deviation-max-mohm]}]
  (and (number? internal-resistance-deviation-actual-mohm)
       (number? internal-resistance-deviation-min-mohm)
       (number? internal-resistance-deviation-max-mohm)
       (or (< internal-resistance-deviation-actual-mohm internal-resistance-deviation-min-mohm)
           (> internal-resistance-deviation-actual-mohm internal-resistance-deviation-max-mohm))))

(defn register-cell-batch-shipment
  "Validate + construct the CELL-BATCH-SHIPMENT registration DRAFT --
  the battery plant's own act of dispatching a real robot handling/
  shipment action releasing a cell-batch onward to a downstream
  device/vehicle assembler (the real upstream hand-off to BOTH
  `cloud-itonami-isic-2630`'s device-unit assembly line and
  `cloud-itonami-isic-2910`'s vehicle powertrain integration -- see
  README `Upstream -> downstream hand-off`). Pure function -- does not
  touch any real plant/MES control system; it builds the RECORD a
  battery plant would keep. `cellworks.governor` independently
  re-verifies the cell-batch's own internal-resistance sufficiency
  against its own acceptance-band bounds, and a double-shipment for the
  same cell-batch, before this is ever allowed to commit."
  [cell-batch-id jurisdiction sequence]
  (when-not (and cell-batch-id (not= cell-batch-id ""))
    (throw (ex-info "cell-batch-shipment: cell_batch_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "cell-batch-shipment: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "cell-batch-shipment: sequence must be >= 0" {})))
  (let [shipment-number (str (str/upper-case jurisdiction) "-CBS-" (zero-pad sequence 6))
        record {"record_id" shipment-number
                "kind" "cell-batch-shipment-draft"
                "cell_batch_id" cell-batch-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "shipment_number" shipment-number
     "certificate" (unsigned-certificate "CellBatchShipment" shipment-number shipment-number)}))

(defn register-safety-certificate
  "Validate + construct the BATTERY SAFETY TEST REPORT registration
  DRAFT -- the battery plant's own act of issuing a real UN 38.3/
  IEC 62133-style Battery Safety Test Report certifying a cell-batch's
  mechanical/electrical-abuse conformance before onward shipment. Pure
  function -- does not touch any real plant/MES control system; it
  builds the RECORD a battery plant would keep. `cellworks.governor`
  independently re-verifies the cell-batch's own end-of-line-defect
  resolution status, and a double-issuance for the same cell-batch,
  before this is ever allowed to commit."
  [cell-batch-id jurisdiction sequence]
  (when-not (and cell-batch-id (not= cell-batch-id ""))
    (throw (ex-info "safety-certificate: cell_batch_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "safety-certificate: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "safety-certificate: sequence must be >= 0" {})))
  (let [certificate-number (str (str/upper-case jurisdiction) "-BSTR-" (zero-pad sequence 6))
        record {"record_id" certificate-number
                "kind" "safety-certificate-draft"
                "cell_batch_id" cell-batch-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "certificate_number" certificate-number
     "certificate" (unsigned-certificate "BatterySafetyTestReport" certificate-number certificate-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))
