(ns cellworks.store
  "SSoT for the lithium-ion battery cell/pack manufacturing actor,
  behind a `Store` protocol so the backend is a swap, not a rewrite --
  the same seam every prior `cloud-itonami-isic-*` actor in this fleet
  uses:

    - `MemStore`     -- atom of EDN. The deterministic default for
                        dev/tests/demo (no deps).
    - `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible
                        EAV store (datalog q / pull / upsert). Pure `.cljc`,
                        so it runs offline AND can be pointed at a real
                        Datomic Local or a kotoba-server pod by swapping
                        `langchain.db`'s `:db-api` (see langchain.kotoba-db).

  Both implement the same protocol and pass the same contract
  (test/cellworks/store_contract_test.clj), which is the whole point:
  the actor, the Cell-Safety Governor and the audit ledger never know
  which SSoT they run on.

  Like `automotive.store`'s dual vehicle-dispatch/conformity-
  certificate history and `bodyshop.store`'s dual body-shell-shipment/
  body-certificate history, this actor has TWO actuation events
  (shipping a cell-batch onward to a downstream device/vehicle
  assembler, issuing a Battery Safety Test Report) acting on the SAME
  entity (a cell-batch), each with its OWN history collection, sequence
  counter and dedicated double-actuation-guard boolean
  (`:cell-batch-shipped?`/`:safety-certified?`, never a `:status`
  value) -- the same discipline every prior sibling governor's guards
  establish, informed by `cloud-itonami-isic-6492`'s status-lifecycle
  bug (ADR-2607071320).

  The ledger stays append-only on every backend: 'which cell-batch was
  screened for an unresolved end-of-line defect, which cell-batch
  shipment was dispatched onward to a downstream assembler, which
  Battery Safety Test Report was issued, on what scheme/jurisdictional
  basis, approved by whom' is always a query over an immutable log --
  the audit trail a community trusting a battery plant needs, and the
  evidence a plant needs if a shipment or certificate decision is later
  disputed."
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [cellworks.registry :as registry]
            [cellworks.robotics :as robotics]
            [langchain.db :as d]))

(defprotocol Store
  (cell-batch [s id])
  (all-cell-batches [s])
  (eol-screen-of [s cell-batch-id] "committed end-of-line quality screening verdict for a cell-batch, or nil")
  (cell-safety-verification-of [s cell-batch-id] "committed cell-safety-rules evidence verification, or nil")
  (ledger [s])
  (shipment-history [s] "the append-only cell-batch-shipment history (cellworks.registry drafts)")
  (certificate-history [s] "the append-only safety-certificate history (cellworks.registry drafts)")
  (next-shipment-sequence [s jurisdiction] "next shipment-number sequence for a jurisdiction/scheme")
  (next-certificate-sequence [s jurisdiction] "next certificate-number sequence for a jurisdiction/scheme")
  (cell-batch-already-shipped? [s cell-batch-id] "has this cell-batch already been shipped onward?")
  (cell-batch-already-certified? [s cell-batch-id] "has this cell-batch's Battery Safety Test Report already been issued?")
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact]   "append one immutable decision fact")
  (with-cell-batches [s cell-batches] "replace/seed the cell-batch directory (map id->cell-batch)"))

;; ----------------------------- demo data -----------------------------

(defn- with-crush-telemetry
  "Merges REAL UN 38.3 T6 crush-test telemetry onto a demo cell-batch's
  base fields -- `cellworks.robotics/crush-telemetry-for` actually runs
  `simulate-crush`'s `physics-2d`-stepped simulation for this
  cell-batch's own `:crush-press-platen-mass-kg` (ADR-2607151600/
  ADR-2607152000), so even the 'already on file' seed data (as if from
  an earlier real crush-test report) is genuinely simulation-derived,
  never hand-typed doubles."
  [base]
  (merge base (select-keys (robotics/crush-telemetry-for base)
                           [:sim-peak-crush-force-n :sim-peak-crush-pressure-mpa])))

(defn demo-data
  "A small, self-contained cell-batch set covering both actuation
  lifecycles (shipping a cell-batch onward to a downstream device/
  vehicle assembler, issuing a Battery Safety Test Report) so the actor
  + tests run offline. `:crush-press-platen-mass-kg` (ADR-2607151600/
  ADR-2607152000) is a permanent cell-batch press-run-configuration
  field (like `:internal-resistance-deviation-actual-mohm`);
  `:sim-peak-crush-force-n`/`:sim-peak-crush-pressure-mpa` are the REAL
  `cellworks.robotics/simulate-crush`-computed telemetry for that field
  (`with-crush-telemetry`), the ground truth `cellworks.robotics/
  simulation-out-of-tolerance?` independently rechecks. batch-5 is
  DELIBERATELY recorded with a much heavier `:crush-press-platen-mass-
  kg` (300 kg) than UN 38.3 T6's own 13 kN crush-force ceiling can
  clear -- a genuine press-run-configuration inconsistency (someone/
  something ran this batch's crush-test cycle on an oversized press
  slide/platen assembly, or logged the wrong platen mass) that the
  real, re-run simulation catches on independent recheck even though
  `:robotics-sim-verified?` was seeded `true` (\"already on file\", i.e.
  someone/something marked it passed without this real check ever
  having run) -- the battery-plant analog of automotive's
  misclassified vehicle-5 / bodyshop's shell-5. batch-1/2/3/4's
  `:crush-press-platen-mass-kg` (80 kg each) is a genuinely consistent
  benchtop/floor-standing crush-test-press moving-platen mass, which
  clears UN 38.3 T6's real 13 kN crush-force ceiling with margin (see
  `cellworks.robotics/un383-t6-crush-force-ceiling-n`)."
  []
  {:cell-batches
   (into {}
         (map (fn [v] [(:id v) (with-crush-telemetry v)]))
         [{:id "batch-1" :batch-name "Meridian 21700 Cell Batch CB-4401"
           :crush-press-platen-mass-kg 80
           :internal-resistance-deviation-actual-mohm 2.0
           :internal-resistance-deviation-min-mohm -5.0
           :internal-resistance-deviation-max-mohm 5.0
           :cell-batch-defect-unresolved? false
           :robotics-sim-verified? false :robotics-sim-record nil
           :cell-batch-shipped? false :safety-certified? false
           :jurisdiction "UN" :status :intake}
          {:id "batch-2" :batch-name "Atlas 21700 Cell Batch CB-1180"
           :crush-press-platen-mass-kg 80
           :internal-resistance-deviation-actual-mohm 2.0
           :internal-resistance-deviation-min-mohm -5.0
           :internal-resistance-deviation-max-mohm 5.0
           :cell-batch-defect-unresolved? false
           :robotics-sim-verified? false :robotics-sim-record nil
           :cell-batch-shipped? false :safety-certified? false
           :jurisdiction "ATL" :status :intake}
          {:id "batch-3" :batch-name "田中セル電池バッチ CB-2215"
           :crush-press-platen-mass-kg 80
           :internal-resistance-deviation-actual-mohm 8.5
           :internal-resistance-deviation-min-mohm -5.0
           :internal-resistance-deviation-max-mohm 5.0
           :cell-batch-defect-unresolved? false
           :robotics-sim-verified? false :robotics-sim-record nil
           :cell-batch-shipped? false :safety-certified? false
           :jurisdiction "CHN" :status :intake}
          {:id "batch-4" :batch-name "佐藤セル電池バッチ CB-3330"
           :crush-press-platen-mass-kg 80
           :internal-resistance-deviation-actual-mohm 2.0
           :internal-resistance-deviation-min-mohm -5.0
           :internal-resistance-deviation-max-mohm 5.0
           :cell-batch-defect-unresolved? true
           :robotics-sim-verified? false :robotics-sim-record nil
           :cell-batch-shipped? false :safety-certified? false
           :jurisdiction "USA" :status :intake}
          {:id "batch-5" :batch-name "鈴木セル電池バッチ CB-1118"
           :crush-press-platen-mass-kg 300
           :internal-resistance-deviation-actual-mohm 2.0
           :internal-resistance-deviation-min-mohm -5.0
           :internal-resistance-deviation-max-mohm 5.0
           :cell-batch-defect-unresolved? false
           :robotics-sim-verified? true :robotics-sim-record nil
           :cell-batch-shipped? false :safety-certified? false
           :jurisdiction "IEC" :status :intake}])})

;; ----------------------------- shared commit logic -----------------------------

(defn- ship-cell-batch!
  "Backend-agnostic `:cell-batch/mark-shipped` -- looks up the
  cell-batch via the protocol and drafts the cell-batch-shipment
  record, and returns {:result .. :cell-batch-patch ..} for the caller
  to persist."
  [s cell-batch-id]
  (let [a (cell-batch s cell-batch-id)
        seq-n (next-shipment-sequence s (:jurisdiction a))
        result (registry/register-cell-batch-shipment cell-batch-id (:jurisdiction a) seq-n)]
    {:result result
     :cell-batch-patch {:cell-batch-shipped? true
                        :shipment-number (get result "shipment_number")}}))

(defn- issue-safety-certificate!
  "Backend-agnostic `:cell-batch/mark-certified` -- looks up the
  cell-batch via the protocol and drafts the Battery Safety Test Report
  record, and returns {:result .. :cell-batch-patch ..} for the caller
  to persist."
  [s cell-batch-id]
  (let [a (cell-batch s cell-batch-id)
        seq-n (next-certificate-sequence s (:jurisdiction a))
        result (registry/register-safety-certificate cell-batch-id (:jurisdiction a) seq-n)]
    {:result result
     :cell-batch-patch {:safety-certified? true
                        :certificate-number (get result "certificate_number")}}))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (cell-batch [_ id] (get-in @a [:cell-batches id]))
  (all-cell-batches [_] (sort-by :id (vals (:cell-batches @a))))
  (eol-screen-of [_ id] (get-in @a [:eol-screens id]))
  (cell-safety-verification-of [_ cell-batch-id] (get-in @a [:verifications cell-batch-id]))
  (ledger [_] (:ledger @a))
  (shipment-history [_] (:shipments @a))
  (certificate-history [_] (:certificates @a))
  (next-shipment-sequence [_ jurisdiction] (get-in @a [:shipment-sequences jurisdiction] 0))
  (next-certificate-sequence [_ jurisdiction] (get-in @a [:certificate-sequences jurisdiction] 0))
  (cell-batch-already-shipped? [_ cell-batch-id] (boolean (get-in @a [:cell-batches cell-batch-id :cell-batch-shipped?])))
  (cell-batch-already-certified? [_ cell-batch-id] (boolean (get-in @a [:cell-batches cell-batch-id :safety-certified?])))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :cell-batch/upsert
      (swap! a update-in [:cell-batches (:id value)] merge value)

      :cell-safety-verification/set
      (swap! a assoc-in [:verifications (first path)] payload)

      :eol-screen/set
      (swap! a assoc-in [:eol-screens (first path)] payload)

      :cell-batch/mark-shipped
      (let [cell-batch-id (first path)
            {:keys [result cell-batch-patch]} (ship-cell-batch! s cell-batch-id)
            jurisdiction (:jurisdiction (cell-batch s cell-batch-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:shipment-sequences jurisdiction] (fnil inc 0))
                       (update-in [:cell-batches cell-batch-id] merge cell-batch-patch)
                       (update :shipments registry/append result))))
        result)

      :cell-batch/mark-certified
      (let [cell-batch-id (first path)
            {:keys [result cell-batch-patch]} (issue-safety-certificate! s cell-batch-id)
            jurisdiction (:jurisdiction (cell-batch s cell-batch-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:certificate-sequences jurisdiction] (fnil inc 0))
                       (update-in [:cell-batches cell-batch-id] merge cell-batch-patch)
                       (update :certificates registry/append result))))
        result)
      nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-cell-batches [s cell-batches] (when (seq cell-batches) (swap! a assoc :cell-batches cell-batches)) s))

(defn seed-db
  "A MemStore seeded with the demo cell-batch set. The deterministic
  default."
  []
  (->MemStore (atom (assoc (demo-data)
                           :verifications {} :eol-screens {} :ledger []
                           :shipment-sequences {} :shipments []
                           :certificate-sequences {} :certificates []))))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  "DataScript/Datomic-style schema: only constraint attrs are declared.
  Map/compound values (verification/eol-screen payloads, ledger facts,
  shipment/certificate records) are stored as EDN strings so
  `langchain.db` doesn't expand them into sub-entities -- the same
  convention every sibling actor's store uses."
  {:cell-batch/id                     {:db/unique :db.unique/identity}
   :verification/cell-batch-id        {:db/unique :db.unique/identity}
   :eol-screen/cell-batch-id          {:db/unique :db.unique/identity}
   :ledger/seq                        {:db/unique :db.unique/identity}
   :shipment/seq                      {:db/unique :db.unique/identity}
   :certificate/seq                   {:db/unique :db.unique/identity}
   :shipment-sequence/jurisdiction    {:db/unique :db.unique/identity}
   :certificate-sequence/jurisdiction {:db/unique :db.unique/identity}})

(defn- enc [v] (pr-str v))
(defn- dec* [s] (when s (edn/read-string s)))

(defn- cell-batch->tx [{:keys [id batch-name
                               crush-press-platen-mass-kg sim-peak-crush-force-n sim-peak-crush-pressure-mpa
                               internal-resistance-deviation-actual-mohm internal-resistance-deviation-min-mohm internal-resistance-deviation-max-mohm
                               cell-batch-defect-unresolved? robotics-sim-verified? robotics-sim-record
                               cell-batch-shipped? safety-certified?
                               jurisdiction status shipment-number certificate-number]}]
  (cond-> {:cell-batch/id id}
    batch-name                                        (assoc :cell-batch/batch-name batch-name)
    crush-press-platen-mass-kg                        (assoc :cell-batch/crush-press-platen-mass-kg crush-press-platen-mass-kg)
    sim-peak-crush-force-n                            (assoc :cell-batch/sim-peak-crush-force-n sim-peak-crush-force-n)
    (some? sim-peak-crush-pressure-mpa)               (assoc :cell-batch/sim-peak-crush-pressure-mpa sim-peak-crush-pressure-mpa)
    internal-resistance-deviation-actual-mohm         (assoc :cell-batch/internal-resistance-deviation-actual-mohm internal-resistance-deviation-actual-mohm)
    internal-resistance-deviation-min-mohm            (assoc :cell-batch/internal-resistance-deviation-min-mohm internal-resistance-deviation-min-mohm)
    internal-resistance-deviation-max-mohm            (assoc :cell-batch/internal-resistance-deviation-max-mohm internal-resistance-deviation-max-mohm)
    (some? cell-batch-defect-unresolved?)             (assoc :cell-batch/cell-batch-defect-unresolved? cell-batch-defect-unresolved?)
    (some? robotics-sim-verified?)                    (assoc :cell-batch/robotics-sim-verified? robotics-sim-verified?)
    (some? robotics-sim-record)                       (assoc :cell-batch/robotics-sim-record (enc robotics-sim-record))
    (some? cell-batch-shipped?)                       (assoc :cell-batch/cell-batch-shipped? cell-batch-shipped?)
    (some? safety-certified?)                         (assoc :cell-batch/safety-certified? safety-certified?)
    jurisdiction                                       (assoc :cell-batch/jurisdiction jurisdiction)
    status                                             (assoc :cell-batch/status status)
    shipment-number                                    (assoc :cell-batch/shipment-number shipment-number)
    certificate-number                                 (assoc :cell-batch/certificate-number certificate-number)))

(def ^:private cell-batch-pull
  [:cell-batch/id :cell-batch/batch-name
   :cell-batch/crush-press-platen-mass-kg :cell-batch/sim-peak-crush-force-n :cell-batch/sim-peak-crush-pressure-mpa
   :cell-batch/internal-resistance-deviation-actual-mohm :cell-batch/internal-resistance-deviation-min-mohm :cell-batch/internal-resistance-deviation-max-mohm
   :cell-batch/cell-batch-defect-unresolved? :cell-batch/robotics-sim-verified? :cell-batch/robotics-sim-record
   :cell-batch/cell-batch-shipped? :cell-batch/safety-certified?
   :cell-batch/jurisdiction :cell-batch/status :cell-batch/shipment-number :cell-batch/certificate-number])

(defn- pull->cell-batch [m]
  (when (:cell-batch/id m)
    {:id (:cell-batch/id m) :batch-name (:cell-batch/batch-name m)
     :crush-press-platen-mass-kg (:cell-batch/crush-press-platen-mass-kg m)
     :sim-peak-crush-force-n (:cell-batch/sim-peak-crush-force-n m)
     :sim-peak-crush-pressure-mpa (:cell-batch/sim-peak-crush-pressure-mpa m)
     :internal-resistance-deviation-actual-mohm (:cell-batch/internal-resistance-deviation-actual-mohm m)
     :internal-resistance-deviation-min-mohm (:cell-batch/internal-resistance-deviation-min-mohm m)
     :internal-resistance-deviation-max-mohm (:cell-batch/internal-resistance-deviation-max-mohm m)
     :cell-batch-defect-unresolved? (boolean (:cell-batch/cell-batch-defect-unresolved? m))
     :robotics-sim-verified? (boolean (:cell-batch/robotics-sim-verified? m))
     :robotics-sim-record (dec* (:cell-batch/robotics-sim-record m))
     :cell-batch-shipped? (boolean (:cell-batch/cell-batch-shipped? m))
     :safety-certified? (boolean (:cell-batch/safety-certified? m))
     :jurisdiction (:cell-batch/jurisdiction m) :status (:cell-batch/status m)
     :shipment-number (:cell-batch/shipment-number m) :certificate-number (:cell-batch/certificate-number m)}))

(defrecord DatomicStore [conn]
  Store
  (cell-batch [_ id]
    (pull->cell-batch (d/pull (d/db conn) cell-batch-pull [:cell-batch/id id])))
  (all-cell-batches [_]
    (->> (d/q '[:find [?id ...] :where [?e :cell-batch/id ?id]] (d/db conn))
         (map #(pull->cell-batch (d/pull (d/db conn) cell-batch-pull [:cell-batch/id %])))
         (sort-by :id)))
  (eol-screen-of [_ id]
    (dec* (d/q '[:find ?p . :in $ ?aid
                :where [?k :eol-screen/cell-batch-id ?aid] [?k :eol-screen/payload ?p]]
              (d/db conn) id)))
  (cell-safety-verification-of [_ cell-batch-id]
    (dec* (d/q '[:find ?p . :in $ ?aid
                :where [?a :verification/cell-batch-id ?aid] [?a :verification/payload ?p]]
              (d/db conn) cell-batch-id)))
  (ledger [_]
    (->> (d/q '[:find ?s ?f :where [?e :ledger/seq ?s] [?e :ledger/fact ?f]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (shipment-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :shipment/seq ?s] [?e :shipment/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (certificate-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :certificate/seq ?s] [?e :certificate/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (next-shipment-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :shipment-sequence/jurisdiction ?j] [?e :shipment-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (next-certificate-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :certificate-sequence/jurisdiction ?j] [?e :certificate-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (cell-batch-already-shipped? [s cell-batch-id]
    (boolean (:cell-batch-shipped? (cell-batch s cell-batch-id))))
  (cell-batch-already-certified? [s cell-batch-id]
    (boolean (:safety-certified? (cell-batch s cell-batch-id))))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :cell-batch/upsert
      (d/transact! conn [(cell-batch->tx value)])

      :cell-safety-verification/set
      (d/transact! conn [{:verification/cell-batch-id (first path) :verification/payload (enc payload)}])

      :eol-screen/set
      (d/transact! conn [{:eol-screen/cell-batch-id (first path) :eol-screen/payload (enc payload)}])

      :cell-batch/mark-shipped
      (let [cell-batch-id (first path)
            {:keys [result cell-batch-patch]} (ship-cell-batch! s cell-batch-id)
            jurisdiction (:jurisdiction (cell-batch s cell-batch-id))
            next-n (inc (next-shipment-sequence s jurisdiction))]
        (d/transact! conn
                     [(cell-batch->tx (assoc cell-batch-patch :id cell-batch-id))
                      {:shipment-sequence/jurisdiction jurisdiction :shipment-sequence/next next-n}
                      {:shipment/seq (count (shipment-history s)) :shipment/record (enc (get result "record"))}])
        result)

      :cell-batch/mark-certified
      (let [cell-batch-id (first path)
            {:keys [result cell-batch-patch]} (issue-safety-certificate! s cell-batch-id)
            jurisdiction (:jurisdiction (cell-batch s cell-batch-id))
            next-n (inc (next-certificate-sequence s jurisdiction))]
        (d/transact! conn
                     [(cell-batch->tx (assoc cell-batch-patch :id cell-batch-id))
                      {:certificate-sequence/jurisdiction jurisdiction :certificate-sequence/next next-n}
                      {:certificate/seq (count (certificate-history s)) :certificate/record (enc (get result "record"))}])
        result)
      nil)
    s)
  (append-ledger! [s fact]
    (d/transact! conn [{:ledger/seq (count (ledger s)) :ledger/fact (enc fact)}])
    fact)
  (with-cell-batches [s cell-batches]
    (when (seq cell-batches) (d/transact! conn (mapv cell-batch->tx (vals cell-batches)))) s))

(defn datomic-store
  "A DatomicStore (langchain.db backend) seeded from `data`
  ({:cell-batches ..}); empty when omitted."
  ([] (datomic-store {}))
  ([{:keys [cell-batches]}]
   (let [s (->DatomicStore (d/create-conn schema))]
     (with-cell-batches s cell-batches))))

(defn datomic-seed-db
  "A DatomicStore seeded with the demo cell-batch set -- the
  Datomic-backed analog of `seed-db`, used to prove protocol parity."
  []
  (datomic-store (demo-data)))
