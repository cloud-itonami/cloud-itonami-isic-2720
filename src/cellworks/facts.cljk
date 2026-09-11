(ns cellworks.facts
  "Battery safety-certification evidence catalog -- the G2-style
  spec-basis table the Cell-Safety Governor checks every
  `:cell-safety-rules/verify` proposal against.

  UNLIKE `automotive.facts`'s per-COUNTRY vehicle type-approval statute
  table or `bodyshop.facts`'s per-country voluntary material-standard
  table, lithium-battery safety certification does not decompose neatly
  into one scheme per ISO3 country: it is a mix of (a) a GLOBAL
  TRANSPORT regime that applies almost everywhere a cell is shipped,
  (b) NATIONAL mandatory product-safety standards, and (c) an
  INTERNATIONAL baseline safety standard that many national regulators
  simply reference/harmonize rather than author their own. This
  catalog's keys reflect that real structure honestly rather than
  forcing a false per-country shape:

    - \"UN\"  -- UN 38.3 (UN Manual of Tests and Criteria, Part III,
                Sub-section 38.3). MANDATORY worldwide for the transport
                (air/sea/road) of ALL lithium metal/lithium-ion cells
                and batteries, administered via the UN Sub-Committee of
                Experts on the Transport of Dangerous Goods and adopted
                downstream by ICAO Technical Instructions / IATA DGR /
                IMDG Code. Covers the T1-T8 test series: altitude
                simulation, thermal cycling, vibration, shock, external
                short-circuit, impact/crush, overcharge, forced
                discharge.
    - \"USA\" -- UL 2054 (UL Solutions / ANSI). US battery-PACK safety
                standard (household and commercial batteries, including
                lithium-ion/lithium-polymer), evaluating the complete
                pack (protection circuit, housing, integration), not
                just the bare cell.
    - \"CHN\" -- GB 31241 (国家市场监督管理总局 / SAMR, current edition
                GB 31241-2022). China's MANDATORY national safety
                standard for lithium-ion cells and batteries used in
                portable electronic equipment -- includes a crush and
                nail-penetration mechanical-abuse test suite (see
                `cellworks.robotics` docstring for why this ns does NOT
                cite a distinct GB 31241 numeric crush-force/
                displacement threshold of its own).
    - \"IEC\" -- IEC 62133-2 (International Electrotechnical
                Commission). The international BASELINE safety standard
                for portable sealed secondary lithium cells/batteries
                that many jurisdictions (e.g. the EU via the harmonized
                EN 62133-2) reference rather than author a distinct
                national standard -- this catalog uses \"IEC\" as a
                stand-in for those jurisdictions honestly, rather than
                inventing a separate national code this ns has no
                confident distinct citation for.

  Coverage is reported HONESTLY: a scheme/jurisdiction not in this table
  has NO spec-basis. Seed values cite official standards-owning bodies;
  this is a starting catalog, not a survey of every market or every
  cell-format's own supplement (e.g. UN 38.3's button-cell/small-cell
  exemptions are out of scope here).")

(def catalog
  {"UN" {:name "UN Transport of Dangerous Goods (global, transport)"
         :owner-authority "UN Sub-Committee of Experts on the Transport of Dangerous Goods (UN SCETDG)"
         :legal-basis "UN Manual of Tests and Criteria, Part III, Sub-section 38.3 (\"UN 38.3\") -- mandatory test regime for the international transport of lithium metal/lithium-ion cells and batteries by air/sea/road"
         :national-spec "UN 38.3 T1-T8 test series (altitude simulation / thermal cycling / vibration / shock / external short-circuit / impact-crush / overcharge / forced-discharge)"
         :provenance "https://www.prba.org/wp-content/uploads/Sub-section-38.3-UN-Manual-6th-Revised-Edition-with-corrections.pdf"
         :required-evidence ["UN 38.3 Test Summary (per Sub-section 38.3, covering all applicable T1-T8 tests)"
                             "Cell/battery UN-number & proper-shipping-name classification record"
                             "T6 impact/crush test report"
                             "T5/T7/T8 (external short-circuit/overcharge/forced-discharge) test report"]}
   "USA" {:name "United States"
          :owner-authority "UL Solutions (Underwriters Laboratories)"
          :legal-basis "UL 2054 -- Standard for Household and Commercial Batteries (pack-level safety, incl. lithium-ion/lithium-polymer)"
          :national-spec "UL 2054 pack-level mechanical/electrical/environmental abuse test program"
          :provenance "https://www.shopulstandards.com/ProductDetail.aspx?UniqueKey=40907"
          :required-evidence ["UL 2054 test report (pack-level mechanical/electrical/environmental abuse)"
                              "Cell manufacturer's cell-level abuse test report (e.g. UL 1642 or equivalent)"
                              "Battery-management / protection-circuit certification record"
                              "Factory follow-up / inspection program enrollment record"]}
   "CHN" {:name "China"
          :owner-authority "国家市场监督管理总局 (SAMR -- State Administration for Market Regulation)"
          :legal-basis "GB 31241-2022 便携式电子产品用锂离子电池和电池组安全技术规范 (Safety technical specification for lithium ion cells and batteries used in portable electronic equipment) -- MANDATORY national standard"
          :national-spec "GB 31241 mechanical (crush/nail-penetration)/electrical/thermal abuse test suite"
          :provenance "https://www.chinesestandard.net/PDF.aspx/GB31241-2022"
          :required-evidence ["GB 31241 test report (crush / nail-penetration / thermal-abuse suite)"
                              "CCC (China Compulsory Certification) registration record, where applicable"
                              "Cell chemistry & rated-capacity declaration"
                              "Factory quality-system audit record"]}
   "IEC" {:name "International (IEC baseline, adopted e.g. by the EU as EN 62133-2)"
          :owner-authority "International Electrotechnical Commission (IEC)"
          :legal-basis "IEC 62133-2:2017(+AMD1:2021) -- Safety requirements for portable sealed secondary lithium cells, and for batteries made from them"
          :national-spec "IEC 62133-2 mechanical (crush/drop/vibration)/electrical (overcharge/forced-discharge/external-short-circuit)/environmental (thermal cycling) abuse test series"
          :provenance "https://webstore.iec.ch/en/publication/32662"
          :required-evidence ["IEC 62133-2 test report (mechanical/electrical/environmental abuse series)"
                              "Cell/battery type-designation & rated-energy declaration"
                              "Manufacturer's quality-plan / production-line surveillance record"
                              "CB Scheme test certificate, where applicable"]}})

(defn spec-basis [scheme] (get catalog scheme))

(defn coverage
  ([] (coverage (keys catalog)))
  ([schemes]
   (let [have (filter catalog schemes)
         missing (remove catalog schemes)]
     {:requested (count schemes)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-isic-2720 R0: " (count catalog)
                 " safety-certification schemes seeded (UN 38.3 transport / "
                 "UL 2054 US / GB 31241 China / IEC 62133-2 international "
                 "baseline). Extend `cellworks.facts/catalog`, never "
                 "fabricate a scheme's requirements.")})))

(defn required-evidence-satisfied?
  [scheme submitted]
  (when-let [{:keys [required-evidence]} (spec-basis scheme)]
    (let [need (count required-evidence)
          have (count (filter (set submitted) required-evidence))]
      (= need have))))

(defn evidence-checklist [scheme]
  (:required-evidence (spec-basis scheme) []))
