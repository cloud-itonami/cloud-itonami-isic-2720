(ns cellworks.cellworksadvisor
  "Cell Advisor client -- the *contained intelligence node* for the
  lithium-ion battery cell/pack manufacturing actor.

  It normalizes cell-batch intake, drafts a per-scheme battery-safety-
  certification evidence checklist, screens cell-batches for an
  unresolved end-of-line defect (internal-resistance/capacity-fade),
  drafts the cell-batch-shipment action (onward to a downstream device/
  vehicle assembler), and drafts the Battery Safety Test Report
  issuance action. CRITICAL: it is a smart-but-untrusted advisor. It
  returns a *proposal* (with a rationale + the fields it cited), never
  a committed record or a real robot shipment/certificate issuance.
  Every output is censored downstream by `cellworks.governor` before
  anything touches the SSoT, and `:actuation/ship-cell-batch`/
  `:actuation/issue-safety-certificate` proposals NEVER auto-commit at
  any phase -- see README `Actuation`.

  Like every sibling actor's advisor, this is a deterministic mock so
  the actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the spec-basis gate
     :cites      [kw|str ..]    ; facts/sources the LLM used -- SCANNED too
     :effect     kw             ; how a commit would mutate the SSoT
     :stake      kw|nil         ; :actuation/ship-cell-batch | :actuation/issue-safety-certificate | nil
     :confidence 0..1}"
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [clojure.string :as str]
            [cellworks.facts :as facts]
            [cellworks.registry :as registry]
            [cellworks.robotics :as robotics]
            [cellworks.store :as store]
            [langchain.model :as model]))

(defn- normalize-intake
  "Directory upsert -- the LLM only normalizes/validates the patch; it
  does not invent the cell-batch, resistance figures or jurisdiction/
  scheme. High confidence, low stakes."
  [_db {:keys [patch]}]
  {:summary    (str "セルバッチ記録更新: " (pr-str (keys patch)))
   :rationale  "入力 patch の正規化のみ。新規事実の生成なし。"
   :cites      (vec (keys patch))
   :effect     :cell-batch/upsert
   :value      patch
   :stake      nil
   :confidence 0.97})

(defn- verify-requirements
  "Per-scheme battery-safety-certification evidence checklist draft.
  `:no-spec?` injects the failure mode we must defend against:
  proposing a checklist for a scheme with NO official spec-basis in
  `cellworks.facts` -- the Cell-Safety Governor must reject this (never
  invent a scheme's requirements)."
  [db {:keys [subject no-spec?]}]
  (let [a (store/cell-batch db subject)
        scheme (if no-spec? "ATL" (:jurisdiction a))
        sb (facts/spec-basis scheme)]
    (if (nil? sb)
      {:summary    (str scheme " の公式spec-basisが見つかりません")
       :rationale  "cellworks.facts に未登録のスキーム。要件を推測で作らない。"
       :cites      []
       :effect     :cell-safety-verification/set
       :value      {:jurisdiction scheme :checklist [] :spec-basis nil}
       :stake      nil
       :confidence 0.9}
      {:summary    (str scheme " (" (:owner-authority sb) ") 向け必要安全認証書類 "
                        (count (:required-evidence sb)) " 件を提案")
       :rationale  (str "公式ソース: " (:provenance sb) " / 根拠規格: " (:legal-basis sb))
       :cites      [(:legal-basis sb) (:provenance sb)]
       :effect     :cell-safety-verification/set
       :value      {:jurisdiction scheme
                    :checklist (:required-evidence sb)
                    :spec-basis (:provenance sb)
                    :legal-basis (:legal-basis sb)}
       :stake      nil
       :confidence 0.9})))

(defn- screen-eol-quality
  "End-of-line internal-resistance/capacity-fade defect screening
  draft. `:cell-batch-defect-unresolved?` on the cell-batch record
  injects the failure mode: the Cell-Safety Governor must HOLD,
  un-overridably, on any unresolved defect."
  [db {:keys [subject]}]
  (let [a (store/cell-batch db subject)]
    (cond
      (nil? a)
      {:summary "対象セルバッチ記録が見つかりません" :rationale "no cell-batch record"
       :cites [] :effect :eol-screen/set :value {:cell-batch-id subject :verdict :unknown}
       :stake nil :confidence 0.0}

      (true? (:cell-batch-defect-unresolved? a))
      {:summary    (str (:batch-name a) ": 未解決の完成検査欠陥(内部抵抗/容量劣化)を検出")
       :rationale  "エンドオブライン品質スクリーニングが未解決の欠陥を検出。人手確認とホールドが必須。"
       :cites      [:end-of-line-quality-check]
       :effect     :eol-screen/set
       :value      {:cell-batch-id subject :verdict :unresolved}
       :stake      nil
       :confidence 0.95}

      :else
      {:summary    (str (:batch-name a) ": 未解決の完成検査欠陥なし")
       :rationale  "エンドオブライン品質欠陥スクリーニング完了。"
       :cites      [:end-of-line-quality-check]
       :effect     :eol-screen/set
       :value      {:cell-batch-id subject :verdict :resolved}
       :stake      nil
       :confidence 0.9})))

(defn- simulate-crush-test
  "Runs the robot UN 38.3 T6 crush-test verification mission
  (`cellworks.robotics`) and drafts its result as a proposal. High
  confidence -- the mission itself is REAL `physics-2d`-simulated
  press-platen/cell crush telemetry derived from the cell-batch's own
  recorded `:crush-press-platen-mass-kg` (ADR-2607151600/
  ADR-2607152000), not an LLM guess; the Cell-Safety Governor still
  independently re-derives :passed? from that same telemetry before any
  `:actuation/ship-cell-batch` proposal may commit -- see `cellworks.
  governor`'s `robotics-simulation-violations`."
  [db {:keys [subject]}]
  (let [a (store/cell-batch db subject)]
    (if (nil? a)
      {:summary "対象セルバッチ記録が見つかりません" :rationale "no cell-batch record"
       :cites [] :effect :cell-batch/upsert :value {:id subject :robotics-sim-verified? false}
       :stake nil :confidence 0.0}
      (let [{:keys [mission actions passed?]} (robotics/simulate-crush-test subject a)]
        {:summary    (str subject ": UN38.3 T6圧壊試験ロボット検証ミッション " (if passed? "合格" "不合格"))
         :rationale  (str "mission=" (:mission/id mission) " actions=" (count actions)
                          " sim-peak-crush-force-n=" (:sim-peak-crush-force-n a))
         :cites      [(:mission/id mission)]
         :effect     :cell-batch/upsert
         :value      {:id subject
                      :robotics-sim-verified? passed?
                      :robotics-sim-record {:mission-id (:mission/id mission)
                                            :actions (mapv #(dissoc % :action) actions)
                                            :passed? passed?}}
         :stake      nil
         :confidence 0.95}))))

(defn- propose-cell-batch-shipment
  "Draft the actual CELL-BATCH-SHIPMENT action -- dispatching a real
  robot handling/shipment action releasing a cell-batch onward to a
  downstream device/vehicle assembler. ALWAYS `:stake :actuation/ship-
  cell-batch` -- this is a REAL-WORLD safety-critical act, never a
  draft the actor may auto-run. See README `Actuation`: no phase ever
  adds this op to a phase's `:auto` set (`cellworks.phase`); the
  governor also always escalates on `:actuation/ship-cell-batch`. Two
  independent layers agree, deliberately."
  [db {:keys [subject]}]
  (let [a (store/cell-batch db subject)]
    {:summary    (str subject " 向けセルバッチ出荷提案"
                      (when a (str " (cell-batch=" (:batch-name a) ")")))
     :rationale  (if a
                   (str "internal-resistance-deviation-actual-mohm=" (:internal-resistance-deviation-actual-mohm a)
                        " spec=[" (:internal-resistance-deviation-min-mohm a) ","
                        (:internal-resistance-deviation-max-mohm a) "]")
                   "セルバッチ記録が見つかりません")
     :cites      (if a [subject] [])
     :effect     :cell-batch/mark-shipped
     :value      {:cell-batch-id subject}
     :stake      :actuation/ship-cell-batch
     :confidence (if (and a (not (registry/cell-batch-resistance-out-of-range? a))) 0.9 0.3)}))

(defn- propose-safety-certificate
  "Draft the actual BATTERY SAFETY TEST REPORT action -- issuing a real
  UN 38.3/IEC 62133-style safety test report certifying a cell-batch's
  mechanical/electrical-abuse conformance before onward shipment.
  ALWAYS `:stake :actuation/issue-safety-certificate` -- this is a
  REAL-WORLD safety-critical act, never a draft the actor may auto-run.
  See README `Actuation`: no phase ever adds this op to a phase's
  `:auto` set (`cellworks.phase`); the governor also always escalates
  on `:actuation/issue-safety-certificate`. Two independent layers
  agree, deliberately."
  [db {:keys [subject]}]
  (let [a (store/cell-batch db subject)]
    {:summary    (str subject " 向けバッテリー安全試験レポート発行提案"
                      (when a (str " (cell-batch=" (:batch-name a) ")")))
     :rationale  (if a
                   "jurisdiction-evidence-checklist referenced"
                   "セルバッチ記録が見つかりません")
     :cites      (if a [subject] [])
     :effect     :cell-batch/mark-certified
     :value      {:cell-batch-id subject}
     :stake      :actuation/issue-safety-certificate
     :confidence (if a 0.9 0.3)}))

(defn infer
  "Route a request to the right proposal generator.
  request: {:op kw :subject id ...op-specific...}"
  [db {:keys [op] :as request}]
  (case op
    :cell-batch/intake                          (normalize-intake db request)
    :cell-safety-rules/verify                   (verify-requirements db request)
    :end-of-line-quality/screen                 (screen-eol-quality db request)
    :robotics/simulate-crush-test                (simulate-crush-test db request)
    :actuation/ship-cell-batch                   (propose-cell-batch-shipment db request)
    :actuation/issue-safety-certificate           (propose-safety-certificate db request)
    {:summary "未対応の操作" :rationale (str op) :cites []
     :effect :noop :stake nil :confidence 0.0}))

;; ----------------------------- Advisor protocol -----------------------------

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

(defn mock-advisor
  "The deterministic advisor (the `infer` logic above). Default everywhere."
  [] (reify Advisor (-advise [_ st req] (infer st req))))

(def ^:private system-prompt
  (str "あなたはリチウムイオン電池セル/パック製造工場の"
       "出荷実行・安全証明書発行エージェントの助言者です。"
       "与えられた事実のみに基づき、提案を1つだけEDNマップで返します。説明や前置きは"
       "一切書かず、EDNだけを出力します。\n"
       "キー: :summary(人向けドラフト) :rationale(根拠/必ず事実から) "
       ":cites(使った事実キーのベクタ) "
       ":effect(:cell-batch/upsert|:cell-safety-verification/set|:eol-screen/set|"
       ":cell-batch/mark-shipped|:cell-batch/mark-certified) "
       "(:robotics/simulate-crush-test も :cell-batch/upsert で "
       ":robotics-sim-verified? を提案する) "
       ":stake(:actuation/ship-cell-batch か :actuation/issue-safety-certificate か nil) :confidence(0..1)。\n"
       "重要: 登録されていないスキームの要件を絶対に創作してはいけません。"
       "spec-basisが無い場合は :cites を空にし confidence を上げないこと。"))

(defn- facts-for [st {:keys [op subject]}]
  (case op
    :cell-safety-rules/verify                   {:cell-batch (store/cell-batch st subject)}
    :end-of-line-quality/screen                 {:cell-batch (store/cell-batch st subject)}
    :robotics/simulate-crush-test                {:cell-batch (store/cell-batch st subject)}
    :actuation/ship-cell-batch                   {:cell-batch (store/cell-batch st subject)}
    :actuation/issue-safety-certificate           {:cell-batch (store/cell-batch st subject)}
    {:cell-batch (store/cell-batch st subject)}))

(defn- parse-proposal
  "Parse the model's EDN proposal defensively. Any parse/shape failure
  yields a safe low-confidence noop so the Cell-Safety Governor
  escalates/holds -- an LLM hiccup can never auto-ship a cell-batch
  action or auto-issue a safety certificate."
  [content]
  (let [p (try (edn/read-string (str/trim (str content)))
               (catch #?(:clj Exception :cljs :default) _ nil))]
    (if (map? p)
      (-> p
          (update :cites #(vec (or % [])))
          (update :confidence #(if (number? %) (double %) 0.0))
          (update :effect #(or % :noop)))
      {:summary "LLM応答を解釈できませんでした" :rationale (str content)
       :cites [] :effect :noop :stake nil :confidence 0.0})))

(defn llm-advisor
  "An advisor backed by a `langchain.model/ChatModel` (real inference)."
  ([chat-model] (llm-advisor chat-model {}))
  ([chat-model gen-opts]
   (reify Advisor
     (-advise [_ st req]
       (let [msgs [{:role :system :content system-prompt}
                   {:role :user :content (str "操作: " (:op req)
                                              "\n対象: " (:subject req)
                                              "\n事実: " (pr-str (facts-for st req)))}]
             resp (model/-generate chat-model msgs gen-opts)]
         (parse-proposal (:content resp)))))))

(defn trace
  "Decision-grounded audit record -- persisted to the :audit channel."
  [request proposal]
  {:t          :cellworksadvisor-proposal
   :op         (:op request)
   :subject    (:subject request)
   :summary    (:summary proposal)
   :rationale  (:rationale proposal)
   :cites      (:cites proposal)
   :confidence (:confidence proposal)})
