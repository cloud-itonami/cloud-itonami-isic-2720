(ns cellworks.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 (com-junkawasaki/root ADR-2607189300)
  for this repo. EVERY id, number, status, rule name and hold reason on
  the generated page is REAL output of this repo's own actor stack
  (`cellworks.operation` -> `cellworks.governor` -> `cellworks.phase`
  -> `cellworks.store`, driven through `langgraph.graph/run*` exactly
  as `cellworks.sim` drives it). Nothing on the page is hand-typed
  domain data: the cell-batch rows are pulled from the store after the
  run, the crush-force telemetry is whatever `cellworks.robotics`'
  real `physics-2d` time-stepped UN 38.3 T6 simulation computed, the
  shipment/certificate numbers are whatever `cellworks.registry`
  drafted, and the HARD-hold table is projected from the append-only
  audit ledger the governor actually wrote.

  Every subject id used below exists in `cellworks.store/demo-data`
  (`batch-1`..`batch-5`) -- cross-checked against the store before the
  scenario was written. No synthetic subject is introduced by this
  namespace, and no field is displayed that the domain does not
  actually carry.

  DETERMINISTIC: no timestamps, no randomness, no locale-dependent
  number formatting (doubles are rounded with `Math/round`, not
  `format`). Two consecutive runs against the same seed are
  byte-identical -- verify with a diff.

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [jp-go-dds.skin]
            [clojure.string :as str]
            [cellworks.governor :as governor]
            [cellworks.operation :as op]
            [cellworks.registry :as registry]
            [cellworks.robotics :as robotics]
            [cellworks.store :as store]
            [langgraph.graph :as g]))

(def ^:private operator
  "The human operator context the demo runs under -- a phase-3 quality
  engineer, the same context `cellworks.sim` uses."
  {:actor-id "op-1" :actor-role :quality-engineer :phase 3})

(defn- exec!
  "Runs one operation through the REAL compiled actor graph."
  [actor tid request]
  (g/run* actor {:request request :context operator} {:thread-id tid}))

(defn- approve!
  "Resumes an actor run parked at `:request-approval` with a real human
  approval. Only ever called on runs that actually escalated -- calling
  it after a HARD hold would re-run the graph and append a duplicate
  hold fact to the ledger."
  [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}}
          {:thread-id tid :resume? true}))

(defn run-demo!
  "Seeds a fresh store, builds the REAL OperationActor and executes a
  scenario covering both ends of this actor's behaviour.

  ONE FULL CLEAN LIFECYCLE -- batch-1 (jurisdiction UN, resistance
  deviation inside its own acceptance band, no unresolved end-of-line
  defect, an 80 kg crush press platen that clears UN 38.3 T6's 13 kN
  ceiling): intake (phase-3 auto-commit, the only auto-eligible op) ->
  cell-safety-rules evidence verification (escalates, approved) ->
  end-of-line quality screening (escalates, approved) -> robot UN 38.3
  T6 crush-test mission (escalates, approved) -> cell-batch shipment
  (ALWAYS escalates; `:actuation/*` is absent from every phase's
  `:auto` set AND is high-stakes in the governor -- two independent
  layers, approved) -> Battery Safety Test Report issuance (same
  posture, approved). Both actuations commit real
  `cellworks.registry` draft records.

  SIX DISTINCT HARD HOLDS -- none of which ever reaches a human,
  because `cellworks.phase/gate` keeps a governor HOLD a HOLD:
    - batch-1  `:evidence-incomplete` + `:robotics-simulation-missing`
               (a shipment attempted before any evidence verification
               or crush-test mission has run)
    - batch-2  `:no-spec-basis` (jurisdiction \"ATL\" is deliberately
               absent from `cellworks.facts/catalog`, so the advisor
               cannot cite an official basis)
    - batch-3  `:cell-batch-resistance-out-of-range` (8.5 mOhm outside
               its own recorded [-5.0, 5.0] band -- recomputed
               independently by the governor, never read off a stored
               verdict)
    - batch-5  `:robotics-simulation-out-of-tolerance` (seeded
               `:robotics-sim-verified? true`, i.e. \"already on file\",
               but its 300 kg platen re-simulates above the 13 kN
               ceiling on independent recheck)
    - batch-4  `:end-of-line-defect-unresolved` (the screening op
               HARD-holds on its own finding)
    - batch-1  `:already-shipped` / `:already-certified` (double
               actuation, off dedicated booleans, never a `:status`)

  Returns the store."
  []
  (let [db (store/seed-db)
        actor (op/build db)]
    ;; --- batch-1: full clean lifecycle -------------------------------
    (exec! actor "b1-intake" {:op :cell-batch/intake :subject "batch-1"
                              :patch {:id "batch-1"
                                      :batch-name "Meridian 21700 Cell Batch CB-4401"}})

    ;; HARD hold: shipping before any evidence verification / crush mission.
    (exec! actor "b1-ship-early" {:op :actuation/ship-cell-batch :subject "batch-1"})

    (exec! actor "b1-verify" {:op :cell-safety-rules/verify :subject "batch-1"})
    (approve! actor "b1-verify")

    (exec! actor "b1-screen" {:op :end-of-line-quality/screen :subject "batch-1"})
    (approve! actor "b1-screen")

    (exec! actor "b1-crush" {:op :robotics/simulate-crush-test :subject "batch-1"})
    (approve! actor "b1-crush")

    (exec! actor "b1-ship" {:op :actuation/ship-cell-batch :subject "batch-1"})
    (approve! actor "b1-ship")

    (exec! actor "b1-cert" {:op :actuation/issue-safety-certificate :subject "batch-1"})
    (approve! actor "b1-cert")

    ;; --- batch-2: no official spec-basis for jurisdiction "ATL" ------
    (exec! actor "b2-verify" {:op :cell-safety-rules/verify :subject "batch-2"})

    ;; --- batch-3: out-of-band internal-resistance deviation ----------
    (exec! actor "b3-verify" {:op :cell-safety-rules/verify :subject "batch-3"})
    (approve! actor "b3-verify")
    (exec! actor "b3-crush" {:op :robotics/simulate-crush-test :subject "batch-3"})
    (approve! actor "b3-crush")
    (exec! actor "b3-ship" {:op :actuation/ship-cell-batch :subject "batch-3"})

    ;; --- batch-5: crush sim "on file" but out of tolerance on recheck -
    (exec! actor "b5-verify" {:op :cell-safety-rules/verify :subject "batch-5"})
    (approve! actor "b5-verify")
    (exec! actor "b5-ship" {:op :actuation/ship-cell-batch :subject "batch-5"})

    ;; --- batch-4: the screening op HARD-holds on its own finding -----
    (exec! actor "b4-screen" {:op :end-of-line-quality/screen :subject "batch-4"})

    ;; --- batch-1 again: double-actuation guards ----------------------
    (exec! actor "b1-ship-again" {:op :actuation/ship-cell-batch :subject "batch-1"})
    (exec! actor "b1-cert-again" {:op :actuation/issue-safety-certificate :subject "batch-1"})
    db))

;; ----------------------------- rendering -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")))

(defn- kw-str
  "Full keyword text including its namespace (`:actuation/ship-cell-batch`
  -> \"actuation/ship-cell-batch\"), unlike `name` which silently drops
  the namespace that carries the meaning here."
  [v]
  (if (keyword? v) (subs (str v) 1) (str v)))

(defn- round1
  "Locale-free 1-decimal rounding. `format` would emit a comma decimal
  separator under some default locales and break determinism across
  machines."
  [v]
  (when (number? v) (/ (Math/round (* (double v) 10.0)) 10.0)))

(defn- last-fact-for [ledger batch-id]
  (last (filter #(= (:subject %) batch-id) ledger)))

(defn- status-cell [ledger batch-id]
  (let [f (last-fact-for ledger batch-id)]
    (cond
      (nil? f) "<span class=\"muted\">no activity</span>"
      (= :governor-hold (:t f))
      (str "<span class=\"critical\">HARD hold &middot; "
           (esc (str/join ", " (map kw-str (:basis f)))) "</span>")
      (= :committed (:t f))
      (str "<span class=\"ok\">committed &middot; " (esc (kw-str (:op f))) "</span>")
      :else (str "<span class=\"muted\">" (esc (kw-str (:t f))) "</span>"))))

(defn- lifecycle-cell [{:keys [cell-batch-shipped? safety-certified?
                               shipment-number certificate-number]}]
  (cond
    (and cell-batch-shipped? safety-certified?)
    (str "<span class=\"ok\">shipped " (esc shipment-number)
         " &middot; certified " (esc certificate-number) "</span>")
    cell-batch-shipped?
    (str "<span class=\"warn\">shipped " (esc shipment-number)
         ", not yet certified</span>")
    :else "<span class=\"muted\">in production</span>"))

(defn- crush-cell
  "The cell-batch's own REAL `physics-2d`-simulated peak crush force
  against UN 38.3 T6's cited 13 kN ceiling, re-checked here through the
  same `cellworks.robotics/simulation-out-of-tolerance?` predicate the
  governor uses -- not a copy of its verdict."
  [b]
  (let [n (round1 (:sim-peak-crush-force-n b))]
    (if (robotics/simulation-out-of-tolerance? b)
      (str "<span class=\"critical\">" (esc n) " N &gt; ceiling</span>")
      (str "<span class=\"ok\">" (esc n) " N</span>"))))

(defn- resistance-cell [b]
  (let [{:keys [internal-resistance-deviation-actual-mohm
                internal-resistance-deviation-min-mohm
                internal-resistance-deviation-max-mohm]} b
        band (str " [" (esc internal-resistance-deviation-min-mohm) ", "
                  (esc internal-resistance-deviation-max-mohm) "]")]
    (if (registry/cell-batch-resistance-out-of-range? b)
      (str "<span class=\"critical\">" (esc internal-resistance-deviation-actual-mohm)
           "</span><span class=\"muted\">" band "</span>")
      (str "<span class=\"ok\">" (esc internal-resistance-deviation-actual-mohm)
           "</span><span class=\"muted\">" band "</span>"))))

(defn- batch-row [ledger {:keys [id batch-name jurisdiction crush-press-platen-mass-kg
                                 cell-batch-defect-unresolved?] :as b}]
  (str "        <tr><td><code>" (esc id) "</code></td>"
       "<td>" (esc batch-name) "</td>"
       "<td>" (esc jurisdiction) "</td>"
       "<td class=\"num\">" (resistance-cell b) "</td>"
       "<td class=\"num\">" (esc crush-press-platen-mass-kg) "</td>"
       "<td class=\"num\">" (crush-cell b) "</td>"
       "<td>" (if cell-batch-defect-unresolved?
                "<span class=\"critical\">unresolved</span>"
                "<span class=\"ok\">none</span>") "</td>"
       "<td>" (lifecycle-cell b) "</td>"
       "<td>" (status-cell ledger id) "</td></tr>"))

(defn- hold-rows
  "Projects every HARD hold this run actually produced out of the
  append-only ledger -- one row per violation, with the governor's own
  rule keyword and its own detail text."
  [ledger]
  (for [f ledger
        :when (= :governor-hold (:t f))
        v (:violations f)]
    (str "        <tr><td><code>" (esc (kw-str (:rule v))) "</code></td>"
         "<td><code>" (esc (kw-str (:op f))) "</code></td>"
         "<td><code>" (esc (:subject f)) "</code></td>"
         "<td>" (esc (:detail v)) "</td></tr>")))

(defn- basis-text [{:keys [basis disposition]}]
  (or (some->> (seq basis) (map kw-str) (str/join "; "))
      (some-> disposition kw-str)
      ""))

(defn- ledger-row [{:keys [t op subject] :as f}]
  (str "        <tr><td>"
       (if (= :governor-hold t)
         "<span class=\"critical\">governor-hold</span>"
         (str "<span class=\"ok\">" (esc (kw-str t)) "</span>"))
       "</td>"
       "<td><code>" (esc (kw-str op)) "</code></td>"
       "<td><code>" (esc subject) "</code></td>"
       "<td>" (esc (basis-text f)) "</td></tr>"))

(defn- record-row [r]
  (str "        <tr><td><code>" (esc (get r "record_id")) "</code></td>"
       "<td>" (esc (get r "kind")) "</td>"
       "<td><code>" (esc (get r "cell_batch_id")) "</code></td>"
       "<td>" (esc (get r "jurisdiction")) "</td>"
       "<td>" (esc (get r "immutable")) "</td></tr>"))

(def ^:private gate-rows
  ;; Static description of this actor's own closed op contract, read off
  ;; `cellworks.phase/phases` and `cellworks.governor/high-stakes` --
  ;; fixed behaviour, so it is documentation rather than run telemetry.
  ["        <tr><td><code>:cell-batch/intake</code></td><td><span class=\"ok\">phase-3 auto-commit when governor-clean (the only auto-eligible op)</span></td></tr>"
   "        <tr><td><code>:cell-safety-rules/verify</code></td><td><span class=\"warn\">human approval at every phase &middot; HARD hold without an official spec-basis</span></td></tr>"
   "        <tr><td><code>:end-of-line-quality/screen</code></td><td><span class=\"warn\">human approval at every phase &middot; HARD-holds on its own unresolved finding</span></td></tr>"
   "        <tr><td><code>:robotics/simulate-crush-test</code></td><td><span class=\"warn\">human approval at every phase &middot; real physics-2d UN 38.3 T6 crush mission</span></td></tr>"
   "        <tr><td><code>:actuation/ship-cell-batch</code></td><td><span class=\"warn\">ALWAYS human approval, at every phase &middot; evidence, crush-sim and internal resistance independently re-checked &middot; double shipment refused</span></td></tr>"
   "        <tr><td><code>:actuation/issue-safety-certificate</code></td><td><span class=\"warn\">ALWAYS human approval, at every phase &middot; unresolved end-of-line defect refused &middot; double issuance refused</span></td></tr>"])

(defn render
  "Renders the operator console from a store `db` that has already been
  driven by `run-demo!` (or any other real scenario). Reads only real
  store/ledger state."
  [db]
  (let [ledger (vec (store/ledger db))
        batches (store/all-cell-batches db)
        holds (hold-rows ledger)]
    (str
     "<!doctype html>\n"
     "<html lang=\"en\"><head><meta charset=\"utf-8\">"
     "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
     "<title>cloud-itonami-isic-2720 &middot; battery cell manufacturing</title><style>"
     (jp-go-dds.skin/dds+skin)
     "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Battery cell &amp; pack manufacturing (ISIC 2720) — Operator Console</h1>\n"
     "</header>\n"
     "<p><span class=\"badge\">read-only sample</span> "
     "<span class=\"badge\">governor-gated</span> "
     "<span class=\"badge\">shipment &amp; safety-report issuance always human-approved</span></p>\n"
     "<p class=\"subtitle\">Build-time generated by <code>cellworks.render-html</code> "
     "(<code>clojure -M:dev:render-html</code>) by actually executing "
     "<code>cellworks.operation</code> → <code>cellworks.governor</code> → "
     "<code>cellworks.store</code>. Every id, number, status and hold reason below is "
     "real actor output; nothing is hand-written.</p>\n"

     "<section class=\"card\">\n"
     "  <h2>Cell batches</h2>\n"
     "  <p class=\"muted\">Internal-resistance deviation is shown against each batch's own recorded acceptance band (mOhm). "
     "Peak crush force is this batch's own <code>physics-2d</code>-simulated UN 38.3 T6 telemetry, re-checked against the cited "
     (esc (round1 robotics/un383-t6-crush-force-ceiling-n)) " N ceiling.</p>\n"
     "  <table>\n"
     "    <thead><tr><th>Batch</th><th>Name</th><th>Jurisdiction</th><th>Resistance dev. (mOhm)</th>"
     "<th>Platen (kg)</th><th>Peak crush force</th><th>EOL defect</th><th>Actuation lifecycle</th><th>Last decision</th></tr></thead>\n"
     "    <tbody>\n"
     (str/join "\n" (map (partial batch-row ledger) batches)) "\n"
     "    </tbody>\n"
     "  </table>\n"
     "</section>\n"

     "<section class=\"card\">\n"
     "  <h2>Action gate (Cell-Safety Governor + phase gate)</h2>\n"
     "  <p class=\"muted\">HARD violations cannot be approved away — a governor HOLD stays a HOLD through the phase gate, so it never reaches a human at all. "
     "Confidence floor " (esc governor/confidence-floor) ".</p>\n"
     "  <table>\n"
     "    <thead><tr><th>Op</th><th>Gate</th></tr></thead>\n"
     "    <tbody>\n"
     (str/join "\n" gate-rows) "\n"
     "    </tbody>\n"
     "  </table>\n"
     "</section>\n"

     "<section class=\"card\">\n"
     "  <h2>HARD holds this run (" (count holds) ")</h2>\n"
     "  <p class=\"muted\">Projected from the append-only audit ledger — the governor's own rule keyword and its own detail text. None of these reached a human approver.</p>\n"
     "  <table>\n"
     "    <thead><tr><th>Rule</th><th>Op</th><th>Batch</th><th>Governor detail</th></tr></thead>\n"
     "    <tbody>\n"
     (str/join "\n" holds) "\n"
     "    </tbody>\n"
     "  </table>\n"
     "</section>\n"

     "<section class=\"card\">\n"
     "  <h2>Audit ledger (" (count ledger) " facts)</h2>\n"
     "  <p class=\"muted\">Append-only decision-fact log — every commit and hold this scenario produced, in order.</p>\n"
     "  <table>\n"
     "    <thead><tr><th>Fact</th><th>Op</th><th>Batch</th><th>Basis</th></tr></thead>\n"
     "    <tbody>\n"
     (str/join "\n" (map ledger-row ledger)) "\n"
     "    </tbody>\n"
     "  </table>\n"
     "</section>\n"

     "<section class=\"card\">\n"
     "  <h2>Draft cell-batch shipment records</h2>\n"
     "  <p class=\"muted\">Immutable drafts built by <code>cellworks.registry</code> when an actuation was approved and committed.</p>\n"
     "  <table>\n"
     "    <thead><tr><th>Record</th><th>Kind</th><th>Batch</th><th>Jurisdiction</th><th>Immutable</th></tr></thead>\n"
     "    <tbody>\n"
     (str/join "\n" (map record-row (store/shipment-history db))) "\n"
     "    </tbody>\n"
     "  </table>\n"
     "</section>\n"

     "<section class=\"card\">\n"
     "  <h2>Draft Battery Safety Test Report records</h2>\n"
     "  <table>\n"
     "    <thead><tr><th>Record</th><th>Kind</th><th>Batch</th><th>Jurisdiction</th><th>Immutable</th></tr></thead>\n"
     "    <tbody>\n"
     (str/join "\n" (map record-row (store/certificate-history db))) "\n"
     "    </tbody>\n"
     "  </table>\n"
     "</section>\n"

     "<footer><p>cloud-itonami-isic-2720 — deterministic build-time render; regenerate with "
     "<code>clojure -M:dev:render-html</code>.</p></footer>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        db (run-demo!)
        f (java.io.File. ^String out)]
    (some-> (.getParentFile f) .mkdirs)
    (spit f (render db))
    (println "wrote" out "("
             (count (store/ledger db)) "ledger facts,"
             (count (store/shipment-history db)) "shipments,"
             (count (store/certificate-history db)) "safety certificates )")))
