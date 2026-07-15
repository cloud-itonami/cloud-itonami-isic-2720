(ns cellworks.governor
  "Cell-Safety Governor -- the independent compliance layer that earns
  the Cell Advisor the right to commit. The LLM has no notion of
  battery-safety certification evidence law, whether a cell-batch's own
  measured end-of-line internal-resistance deviation actually stays
  within its own recorded acceptance-band bounds, whether an
  end-of-line-detected defect against the cell-batch has actually
  stayed unresolved, or when an act stops being a draft and becomes a
  real-world robot cell-batch shipment or Battery Safety Test Report
  issuance, so this MUST be a separate system able to *reject* a
  proposal and fall back to HOLD -- the battery-plant analog of
  `cloud-itonami-isic-6512`'s CasualtyGovernor.

  Seven checks, in priority order, ALL HARD violations: a human approver
  CANNOT override them (you don't get to approve your way past a
  fabricated safety-certification spec-basis, incomplete evidence, a
  robot UN 38.3 T6 crush-test simulation that never ran or that
  independently re-checks out-of-tolerance, an out-of-spec cell-batch
  internal-resistance deviation, an unresolved end-of-line defect, or a
  double shipment/certificate-issuance). The confidence/actuation gate
  is SOFT: it asks a human to look (low confidence / actuation), and
  the human may approve -- but see `cellworks.phase`: for `:stake
  :actuation/ship-cell-batch`/`:actuation/issue-safety-certificate` (a
  real safety-critical act) NO phase ever allows auto-commit either.
  Two independent layers agree that actuation is always a human call.

    1. Spec-basis                  -- did the cell-safety-rules
                                       evidence proposal cite an
                                       OFFICIAL source
                                       (`cellworks.facts`), or invent
                                       one?
    2. Evidence incomplete         -- for `:actuation/ship-cell-
                                       batch`/`:actuation/issue-safety-
                                       certificate`, has the cell-batch
                                       actually been verified with a
                                       full safety-certification
                                       evidence checklist (UN 38.3 test
                                       summary / cell-level or pack-level
                                       abuse test report / classification
                                       record etc.) on file?
    3. Robot simulation missing or
       independently out-of-
       tolerance                    -- for `:actuation/ship-cell-
                                       batch`, has the robot UN 38.3 T6
                                       crush-test verification mission
                                       (`cellworks.robotics`) actually
                                       run and been recorded on the
                                       cell-batch (`:robotics-sim-
                                       verified?`)? AND INDEPENDENTLY
                                       recompute whether the cell-
                                       batch's own recorded REAL
                                       `physics-2d`-simulated crush-
                                       force telemetry (`:sim-peak-
                                       crush-force-n`, from
                                       ADR-2607151600/ADR-2607152000's
                                       real time-stepped simulation)
                                       exceeds UN 38.3 T6's own real,
                                       cited 13 kN crush-force ceiling
                                       (`cellworks.robotics/simulation-
                                       out-of-tolerance?`), ignoring
                                       whatever :passed? verdict the
                                       mission run itself stored -- the
                                       same 'ground truth, not
                                       self-report' discipline check 4
                                       below uses for internal
                                       resistance.
    4. Cell-batch internal
       resistance out of range      -- for `:actuation/ship-cell-
                                       batch`, INDEPENDENTLY recompute
                                       whether the cell-batch's own
                                       measured end-of-line internal-
                                       resistance deviation falls
                                       outside its own recorded
                                       acceptance-band bounds
                                       (`cellworks.registry/cell-batch-
                                       resistance-out-of-range?`) --
                                       needs no proposal inspection or
                                       stored-verdict lookup at all. A
                                       further instance of this fleet's
                                       two-sided range check family
                                       (see `cellworks.registry`'s ns
                                       docstring for the lineage).
    5. End-of-line defect
       unresolved                    -- reported by THIS proposal
                                       itself (an `:end-of-line-
                                       quality/screen` that just found
                                       an unresolved defect), or
                                       already on file for the
                                       cell-batch (`:end-of-line-
                                       quality/screen`/`:actuation/
                                       issue-safety-certificate`).
                                       Evaluated UNCONDITIONALLY (not
                                       scoped to a specific op), the
                                       SAME discipline `casualty.
                                       governor/sanctions-violations`/
                                       `automotive.governor/end-of-
                                       line-defect-unresolved-
                                       violations`/`bodyshop.governor/
                                       weld-quality-defect-unresolved-
                                       violations` (prior siblings)
                                       established -- exercised in
                                       tests/demo via `:end-of-line-
                                       quality/screen` DIRECTLY, not via
                                       an actuation op against an
                                       unscreened cell-batch -- see
                                       this ns's own test suite.
    6. Confidence floor / actuation
       gate                          -- LLM confidence below threshold,
                                       OR the op is `:actuation/ship-
                                       cell-batch`/`:actuation/issue-
                                       safety-certificate` (REAL
                                       safety-critical acts) ->
                                       escalate.

  Two more guards, double-shipment/double-certificate-issuance
  prevention, are enforced but NOT listed as numbered HARD checks
  above because they need no upstream comparison at all --
  `already-shipped-violations`/`already-certified-violations` refuse
  to ship a cell-batch action/issue a Battery Safety Test Report for
  the SAME cell-batch twice, off dedicated `:cell-batch-shipped?`/
  `:safety-certified?` facts (never a `:status` value) -- the SAME
  'check a dedicated boolean, not status' discipline every prior
  sibling governor's guards establish, informed by
  `cloud-itonami-isic-6492`'s status-lifecycle bug (ADR-2607071320)."
  (:require [cellworks.facts :as facts]
            [cellworks.registry :as registry]
            [cellworks.robotics :as robotics]
            [cellworks.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Shipping a real cell-batch onward to a downstream device/vehicle
  assembler and issuing a real Battery Safety Test Report are the two
  real-world actuation events this actor performs -- a two-member set,
  matching every prior dual-actuation sibling's shape."
  #{:actuation/ship-cell-batch :actuation/issue-safety-certificate})

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  "A `:cell-safety-rules/verify` (or actuation) proposal with no
  spec-basis citation is a HARD violation -- never invent a scheme's
  battery-safety-certification requirements."
  [{:keys [op]} proposal]
  (when (contains? #{:cell-safety-rules/verify :actuation/ship-cell-batch :actuation/issue-safety-certificate} op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :spec-basis) (nil? (:spec-basis value))))
        [{:rule :no-spec-basis
          :detail "公式spec-basisの引用が無い提案は電池安全認証要件として扱えない"}]))))

(defn- evidence-incomplete-violations
  "For `:actuation/ship-cell-batch`/`:actuation/issue-safety-
  certificate`, the scheme's required safety-certification evidence
  (UN 38.3 test summary / cell-level or pack-level abuse test report /
  classification record etc.) must actually be satisfied -- do not
  trust the advisor's self-reported confidence alone."
  [{:keys [op subject]} st]
  (when (contains? #{:actuation/ship-cell-batch :actuation/issue-safety-certificate} op)
    (let [a (store/cell-batch st subject)
          verification (store/cell-safety-verification-of st subject)]
      (when-not (and verification
                     (facts/required-evidence-satisfied?
                      (:jurisdiction a) (:checklist verification)))
        [{:rule :evidence-incomplete
          :detail "スキームの必要安全認証書類(UN38.3試験サマリー/セルまたはパックレベル濫用試験報告書/分類記録等)が充足していない状態での提案"}]))))

(defn- robotics-simulation-violations
  "For `:actuation/ship-cell-batch`: HARD hold if the robot UN 38.3 T6
  crush-test verification mission (`cellworks.robotics`) never ran and
  was recorded on the cell-batch (`:robotics-sim-verified?`), OR if it
  did but an INDEPENDENT recompute of the cell-batch's own REAL
  `physics-2d`-simulated crush-force telemetry (`:sim-peak-crush-force-
  n`, ADR-2607151600/ADR-2607152000 -- `cellworks.robotics/simulation-
  out-of-tolerance?`) exceeds UN 38.3 T6's own cited 13 kN ceiling right
  now -- never trusts the mission's own stored :passed? verdict alone,
  the same discipline `cell-batch-resistance-out-of-range-violations`
  below uses for internal resistance."
  [{:keys [op subject]} st]
  (when (= op :actuation/ship-cell-batch)
    (let [a (store/cell-batch st subject)]
      (cond
        (not (:robotics-sim-verified? a))
        [{:rule :robotics-simulation-missing
          :detail (str subject " のUN38.3 T6圧壊試験ロボット検証ミッションが未実行・未合格")}]

        (robotics/simulation-out-of-tolerance? a)
        [{:rule :robotics-simulation-out-of-tolerance
          :detail (str subject " の実測圧壊力(" (:sim-peak-crush-force-n a)
                       "N)が独立再検証でUN38.3 T6許容上限(" robotics/un383-t6-crush-force-ceiling-n "N)を超過")}]))))

(defn- cell-batch-resistance-out-of-range-violations
  "For `:actuation/ship-cell-batch`, INDEPENDENTLY recompute whether
  the cell-batch's own internal-resistance deviation falls outside its
  own recorded acceptance-band bounds via `cellworks.registry/cell-
  batch-resistance-out-of-range?` -- needs no proposal inspection or
  stored-verdict lookup at all, since its inputs are permanent
  ground-truth fields already on the cell-batch."
  [{:keys [op subject]} st]
  (when (= op :actuation/ship-cell-batch)
    (let [a (store/cell-batch st subject)]
      (when (registry/cell-batch-resistance-out-of-range? a)
        [{:rule :cell-batch-resistance-out-of-range
          :detail (str subject " の実測内部抵抗偏差(" (:internal-resistance-deviation-actual-mohm a)
                      "mOhm)が許容範囲[" (:internal-resistance-deviation-min-mohm a) ","
                      (:internal-resistance-deviation-max-mohm a) "]mOhmを逸脱")}]))))

(defn- end-of-line-defect-unresolved-violations
  "An unresolved end-of-line-detected defect (internal-resistance/
  capacity-fade) -- reported by THIS proposal (e.g. an `:end-of-line-
  quality/screen` that itself just found one), or already on file in
  the store for the cell-batch (`:end-of-line-quality/screen`/
  `:actuation/issue-safety-certificate`) -- is a HARD, un-overridable
  hold. Evaluated UNCONDITIONALLY (not scoped to a specific op) so the
  screening op itself can HARD-hold on its own finding."
  [{:keys [op subject]} proposal st]
  (let [hit-in-proposal? (= :unresolved (get-in proposal [:value :verdict]))
        cell-batch-id (when (contains? #{:end-of-line-quality/screen :actuation/issue-safety-certificate} op) subject)
        hit-on-file? (and cell-batch-id (= :unresolved (:verdict (store/eol-screen-of st cell-batch-id))))]
    (when (or hit-in-proposal? hit-on-file?)
      [{:rule :end-of-line-defect-unresolved
        :detail "未解決の完成検査欠陥(内部抵抗/容量劣化)がある状態での適合証明書発行提案は進められない"}])))

(defn- already-shipped-violations
  "For `:actuation/ship-cell-batch`, refuses to ship a cell-batch
  action for the SAME cell-batch twice, off a dedicated `:cell-batch-
  shipped?` fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :actuation/ship-cell-batch)
    (when (store/cell-batch-already-shipped? st subject)
      [{:rule :already-shipped
        :detail (str subject " は既に出荷実行済み")}])))

(defn- already-certified-violations
  "For `:actuation/issue-safety-certificate`, refuses to issue a
  Battery Safety Test Report for the SAME cell-batch twice, off a
  dedicated `:safety-certified?` fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :actuation/issue-safety-certificate)
    (when (store/cell-batch-already-certified? st subject)
      [{:rule :already-certified
        :detail (str subject " は既に安全証明書発行済み")}])))

(defn check
  "Censors a Cell Advisor proposal against the governor rules. Returns
  {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (spec-basis-violations request proposal)
                           (evidence-incomplete-violations request st)
                           (robotics-simulation-violations request st)
                           (cell-batch-resistance-out-of-range-violations request st)
                           (end-of-line-defect-unresolved-violations request proposal st)
                           (already-shipped-violations request st)
                           (already-certified-violations request st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (high-stakes (:stake proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
