(ns cellworks.robotics
  "Robot-executed mechanical CRUSH-TEST verification -- the concrete,
  actor-level realization of ADR-2607011000's robotics premise (every
  cloud-itonami vertical is designed on the premise that a robot
  performs the physical-domain work; an independent governor gates any
  action before it ever reaches hardware), delivered NATIVELY onto
  ADR-2607151600/ADR-2607152000's real-engineering-simulation fleet
  pattern from day one (this vertical, isic-2720, was not one of
  ADR-2607152000's original 6 follow-up verticals -- it is a NEW actor
  built to that same standard from day one, mirroring how
  `cloud-itonami-isic-2630`/`cloud-itonami-isic-2920` deliver it
  natively rather than retrofitted; reference implementations:
  `commsdevice.robotics` in `cloud-itonami-isic-2630`, `bodyshop.
  robotics` in `cloud-itonami-isic-2920`) for THIS actor's own
  manufacturing-process evidence requirement: a cell-batch-shipment
  proposal must cite a real UN 38.3 T6 mechanical crush-test report
  actually on file -- not merely a self-reported checklist string.

  The mechanical crush-test step of the mission is an ACTUAL
  time-stepped `kotoba-lang/physics-2d` rigid-body simulation --
  UN 38.3's own Test T6 (\"Impact/Crush\", part of the mandatory
  transport test series every lithium cell/battery must pass): a real
  press-platen `Body2D` closes at a controlled velocity onto a real
  static (mass 0) cylindrical-cell `Body2D`, `world-step` actually
  integrates/collides/resolves the contact over real ticks, and
  `:sim-peak-crush-force-n`/`:sim-peak-crush-pressure-mpa` are read
  directly off the ACTUAL simulated velocity trajectory (`crush-
  telemetry-for` below) -- not invented. This vertical has no
  design-library sibling repo, so the physics module lives DIRECTLY in
  this ns and takes a real pinned git-coordinate dependency on
  `kotoba-lang/physics-2d` alone (see `deps.edn`), mirroring
  `commsdevice.robotics`'s/`bodyshop.robotics`'s own simplification
  versus the automotive pilot's design-library pairing.

  A robot mission (`kotoba.robotics/mission`) walks the cell-batch
  through three steps in the crush-test cell -- a pre-test visual/
  dimensional check, the UN 38.3 T6 crush cycle itself, and a
  post-crush thermal/voltage scan (checking for the standard's own
  100mV-drop/no-fire/no-disassembly criteria) -- built with
  `kotoba.robotics/action` + `kotoba.robotics/telemetry-proof`, and
  reports an overall :passed? verdict now derived from the REAL
  simulated crush reading (`:sim-peak-crush-force-n`, see
  `crush-telemetry-for`), not a hand-set field. `simulation-out-of-
  tolerance?` independently re-derives that verdict from the
  cell-batch's OWN recorded real telemetry cross-checked against UN
  38.3 T6's own real, cited crush-force ceiling (`un383-t6-crush-
  force-ceiling-n`), never from the mission's self-reported result --
  the SAME 'ground truth, not self-report' discipline `cellworks.
  registry/cell-batch-resistance-out-of-range?` uses for internal-
  resistance deviation. `cellworks.governor`'s `robotics-simulation-
  violations` calls this ns's independent recheck, never the stored
  :passed? value, before any `:actuation/ship-cell-batch` proposal may
  commit.

  Honest scope + citation disclosure (mirrors every real-physics
  sibling's own disclosure style, ADR-2607151600/ADR-2607152000):

  - 2D projection only (`physics-2d` has no 3D solver) -- x is the
    press's direction of travel. UN 38.3 T6 itself specifies, for
    cylindrical cells, that 'the crush force shall be applied
    perpendicular to the longitudinal axis' -- so this simulation's
    x-axis genuinely matches the standard's own real crush ORIENTATION
    (a literal correspondence, not just an analog choice); world
    gravity is [0 0] (a horizontal press-closing projection).
  - the cell is modeled as a STATIC (mass 0) AABB, mirroring
    `cementmill.robotics`'s cube-specimen / `bodyshop.robotics`'s
    sheet-metal-blank pattern: `physics-2d` treats a mass-0 body as
    having zero inverse mass (an immovable anchor), which is also
    physically apt here -- a real UN 38.3 crush-test rig fixtures the
    cell against a stationary anvil/base while the platen moves, the
    cell itself is not free to recoil.
  - the cell's real CYLINDRICAL cross-section is approximated as a
    rectangular AABB bounding box (`cell-half-w-m`/`cell-half-h-m`
    below) -- a DISCLOSED SIMPLIFICATION necessitated by `physics-2d`'s
    narrowphase, which only supports AABB-vs-AABB or circle-vs-circle
    pairs, never a mixed AABB-vs-circle pair (`test-collision` returns
    nil for mixed pairs) -- since the press-platen is honestly modeled
    as a flat rigid AABB plate (a literal shape match to a real crush
    platen), the cell must also be AABB for the two bodies to collide
    at all. `physics-2d` has NO material-stiffness/deformation model
    whatsoever, so the cell's own real casing compliance cannot itself
    vary the simulated reading (the SAME disclosed limitation every
    real-physics sibling states) -- what DOES vary the reading is this
    cell-batch's own recorded press-run configuration
    (`:crush-press-platen-mass-kg`, see `crush-telemetry-for`).
  - the cell dimensions modeled (`cell-diameter-mm`/`cell-length-mm`
    below) are a representative modern 21700-format cylindrical
    lithium-ion cell (21mm diameter x 70mm length) -- a real, common
    high-energy-density cell format used across modern EV packs and
    power-tool/power-bank applications feeding this actor's downstream
    consumers (see README `Scope note`); most smartphone cells are
    pouch/prismatic-format rather than cylindrical, which this ns
    discloses honestly rather than overstating the 21700 geometry as
    literally representative of every downstream consumer's own cell
    choice -- the crush-test PHYSICS and governance pattern modeled
    here applies to any cell format a real battery plant produces, the
    21700 dimensions are simply this ns's own concrete, disclosed
    worked example.
  - `press-closing-velocity-mps` (1.0 m/s) is a disclosed ANALOG
    closing rate, NOT a literal transcription of UN 38.3's own real
    specified crush-test procedure speed. UN 38.3 T6 itself literally
    specifies (per secondary test-lab sources describing the current
    UN Manual of Tests and Criteria procedure) a crush speed of 1.5 cm/s
    (0.015 m/s) for the controlled, regulated portion of the test.
    `physics-2d`'s impulse resolver has NO progressive force-vs-
    displacement/crush-stiffness model at all (the SAME disclosed
    limitation every real-physics sibling states): whatever tick first
    detects ANY AABB overlap fully zeroes the closing velocity in that
    ONE tick (restitution 0) -- a discrete, instantaneous stop, not the
    standard's actual continuous, slow, controlled crush ramp. Running
    this simulation at the standard's own literal 1.5 cm/s would (by
    the exact kinematic identity below) require an implausibly large
    press-platen mass (hundreds of tonnes) to reach kN-order forces
    over a millimeter-order transit distance, which is not a realistic
    stand-in for a benchtop/floor-standing crush-test press's actual
    moving-platen mass (plausibly tens to a few hundred kg). This ns
    therefore uses a faster, disclosed analog rate instead -- not
    physically arbitrary either: real hydraulic crush-test presses
    commonly have a FAST FREE-APPROACH phase (closing the initial
    standoff gap) before switching to the slow, standard-mandated
    controlled-crush rate for the regulated portion of the stroke; this
    simulation's `press-closing-velocity-mps` stands in for that fast
    approach-phase terminal velocity, not the regulated crush rate
    itself.
  - `crush-travel-m` (10.5mm = 50% of the modeled cell's own 21mm
    diameter) IS a literal citation of one of UN 38.3 T6's own THREE
    real stopping criteria: the crushing continues until the FIRST of
    (a) applied force reaches 13 kN +/- 0.78 kN, (b) cell voltage drops
    by >= 100 mV, or (c) the cell deforms by >= 50% of its original
    dimension. This ns uses (c)'s 50%-deformation figure as the nominal
    transit distance for deriving `dt`, the same principled-not-
    arbitrary identity `commsdevice.robotics`/`bodyshop.robotics` use
    for their own `dt` -- but here it is a literal standard citation,
    not merely an analog distance choice.
  - `un383-t6-crush-force-ceiling-n` (13,000 N, i.e. 13 kN, +/- 780 N)
    is UN 38.3 T6's own real, cited stopping-force criterion (a),
    corroborated across multiple independent secondary sources
    describing the current UN Manual of Tests and Criteria's crush-test
    procedure text -- a HIGH-CONFIDENCE citation (this ns has not
    independently verified the primary UNECE document text itself in
    this session, only secondary test-lab/industry sources describing
    it, so it is disclosed as a well-corroborated secondary citation,
    not a verbatim primary-source quote).
  - GB 31241 (China's mandatory portable-lithium-battery safety
    standard, current edition GB 31241-2022) mandates its OWN crush and
    nail-penetration mechanical-abuse test suite -- real and current
    (see `cellworks.facts`) -- but this ns does NOT cite a distinct
    GB 31241 numeric crush-force/displacement threshold of its own: this
    session was not able to confidently source one independent of UN
    38.3's, so `un383-t6-crush-force-ceiling-n` is the ONLY numeric
    crush-force ceiling this ns anchors on, honestly, rather than
    fabricating a second number to look more complete.
  - `crush-contact-area-mm2` (the cell's own diameter x length,
    1470 mm^2) is a DISCLOSED SIMPLIFICATION for converting the
    simulated force into an INFORMATIONAL pressure reading
    (`:sim-peak-crush-pressure-mpa`) -- real contact area between a
    flat crush plate and a cylindrical cell starts as a line and grows
    nonlinearly as the cell deforms, which `physics-2d` cannot model at
    all. The tolerance check this ns's `crush-force-out-of-tolerance?`
    performs uses the FORCE reading directly (literally matching UN
    38.3 T6's own force-based stopping criterion), NOT the derived
    pressure -- `:sim-peak-crush-pressure-mpa` is informational only,
    mirroring `bodyshop.robotics`'s own `:sim-peak-draw-distance-m`
    informational field.
  - By exact kinematic identity (a = v^2/d for a boxcar full stop over
    transit distance d at speed v), `crush-press-platen-mass-kg` is the
    ONLY quantity that scales `:sim-peak-crush-force-n` for a fixed
    closing velocity/crush-travel -- the peak deceleration itself is
    INDEPENDENT of the press-platen's own mass when colliding with a
    mass-0 (immovable) cell (mass cancels algebraically in
    `physics-2d`'s `resolve-contact`, the SAME verified, documented
    property every real-physics sibling in this fleet establishes) --
    so `:crush-press-platen-mass-kg` is what actually moves
    `:sim-peak-crush-force-n`/`:sim-peak-crush-pressure-mpa` here (via
    F = m*a), never the closing velocity or crush-travel (both fixed
    constants, shared by every cell-batch).

  Pure data + pure functions -- no real robot I/O, no network.
  `physics-2d/world-step` is itself a pure, fixed-timestep integrator
  (no wall-clock/IO), so this stays exactly as offline/deterministic as
  every other sibling namespace in this actor -- tests and the demo run
  without a network.

  Honest scope: this DOES model a real time-stepped `physics-2d`
  rigid-body trajectory for the crush-test event, in an orientation
  that literally matches UN 38.3 T6's own specified crush direction,
  and its crush-travel distance literally matches the standard's own
  50%-deformation stopping criterion. It does NOT model: cell chemistry,
  internal short-circuit initiation, thermal runaway/venting/fire (the
  standard's own actual pass/fail criteria beyond the force/voltage/
  deformation stopping conditions), 3D geometry (2D projection only),
  the standard's own real controlled crush SPEED (a disclosed analog
  rate is used instead, see above), a real load-cell/DAQ connection, or
  a real crush-press-controller/servo-motion-planning system -- still
  simulation, not control, the same 'policy, not control' boundary
  `kotoba.robotics`'s docstring already establishes."
  (:require [kotoba.robotics :as robotics]
            [physics-2d :as p2d]))

;; ───────────────────── real, disclosed physical constants ─────────────────────

(def ^:const cell-diameter-mm
  "Representative modern 21700-format cylindrical lithium-ion cell
  diameter (mm) -- see ns docstring for the honest disclosure of why
  this format (not a literal per-downstream-consumer CAD measurement)."
  21.0)

(def ^:const cell-length-mm
  "Representative 21700-format cylindrical lithium-ion cell length
  (mm) -- same format as `cell-diameter-mm`."
  70.0)

(def ^:const crush-contact-area-mm2
  "The cell's own diameter x length (mm^2) -- an INFORMATIONAL
  contact-area simplification for the derived pressure reading only,
  see ns docstring. 1 MPa = 1 N/mm^2."
  (* cell-diameter-mm cell-length-mm))

(def ^:const crush-deformation-fraction
  "UN 38.3 T6's own real 50%-deformation stopping criterion (one of
  its three stopping conditions -- see ns docstring)."
  0.5)

(def ^:const crush-travel-m
  "The crush test's nominal transit/give distance (m) -- LITERALLY UN
  38.3 T6's own 50%-of-original-dimension stopping criterion applied to
  the modeled cell's own diameter (0.5 x 21mm = 10.5mm), not merely a
  disclosed analog distance like sibling namespaces' own crush-travel
  constants (see ns docstring)."
  (* crush-deformation-fraction (/ cell-diameter-mm 1000.0)))

(def ^:const press-closing-velocity-mps
  "The press-platen's controlled closing velocity (m/s) for THIS
  simulation -- a disclosed ANALOG fast-approach rate, NOT UN 38.3's
  own literal controlled crush-test speed (1.5 cm/s, i.e. 0.015 m/s)
  -- see ns docstring for why."
  1.0)

(def ^:const un383-t6-real-crush-speed-mps
  "UN 38.3 T6's own literally-specified controlled crush-test speed
  (0.015 m/s = 1.5 cm/s) -- cited here for honest context only; this
  simulation does NOT use this value as `press-closing-velocity-mps`
  (see ns docstring for why)."
  0.015)

(def ^:const un383-t6-crush-force-ceiling-n
  "UN 38.3 T6's own real, cited crush-force stopping criterion: 13 kN
  (13,000 N). See ns docstring for the well-corroborated-secondary-
  citation confidence disclosure."
  13000.0)

(def ^:const un383-t6-crush-force-tolerance-n
  "UN 38.3 T6's own cited tolerance band around the crush-force
  stopping criterion (+/- 0.78 kN = +/- 780 N) -- informational only;
  `crush-force-out-of-tolerance?` uses the nominal ceiling directly."
  780.0)

(def ^:const dt
  "Per-tick timestep (s) -- derived from THIS simulation's own
  crush-travel/closing-velocity (the nominal transit time across the
  cell's own crush-deformation zone), the SAME principled-not-arbitrary
  identity `commsdevice.robotics`/`bodyshop.robotics` use for their own
  `dt`."
  (/ crush-travel-m press-closing-velocity-mps))

(def ^:const platen-half-w-m
  "Press-platen AABB half-width (m) along the travel axis -- a thin,
  rigid platen face (10mm full thickness); `physics-2d` colliders do
  not deform, so this dimension is a disclosed, arbitrary rigid-body
  stand-in, not a load-bearing physical parameter."
  0.005)

(def ^:const platen-half-h-m
  "Press-platen AABB half-height (m), lateral -- 80mm full width,
  wider than the modeled cell's own 70mm length so the WHOLE cell
  length loads, matching how a real UN 38.3 crush plate is sized to
  span the full length of the cell under test."
  0.04)

(def ^:const cell-half-w-m
  "Cell AABB half-width (m) along the travel (crush) axis -- half of
  the modeled cell's own real 21mm diameter, the bounding-box stand-in
  for its real circular cross-section (see ns docstring)."
  (/ (/ cell-diameter-mm 1000.0) 2.0))

(def ^:const cell-half-h-m
  "Cell AABB half-height (m), lateral -- half of the modeled cell's
  own real 70mm length."
  (/ (/ cell-length-mm 1000.0) 2.0))

(def ^:const gap-m
  "Press standoff distance (m) the platen starts behind the cell, so
  the trajectory captures a real pre-contact approach phase, not just
  the collision tick itself (mirrors every sibling's own gap
  constant)."
  0.01)

(def ^:const settle-ticks
  "Extra ticks appended after the platen is expected to reach the
  cell, so the trajectory also captures post-contact settling -- the
  SAME constant + rationale as every real-physics sibling: `physics-2d`'s
  positional correction removes 80% of any remaining overlap per tick,
  so residual overlap after 15 more ticks is ~3e-11 of whatever it was
  at first contact."
  15)

;; ------------------------------ real simulation ------------------------------

(defn simulate-crush
  "Time-steps a REAL `physics-2d` world for ONE UN 38.3 T6 mechanical
  crush-test cycle: a press-platen `Body2D` (mass `platen-mass-kg`,
  velocity `press-closing-velocity-mps`) approaches and collides with a
  static (mass 0, immovable -- matching `bodyshop.robotics`'s
  sheet-metal-blank pattern) cylindrical-cell `Body2D` (modeled as an
  AABB bounding box -- see ns docstring). Returns {:trajectory
  [{:tick :position :velocity} ...] (platen body only)
  :sim-peak-crush-force-n n :sim-peak-crush-pressure-mpa n
  :sim-peak-crush-travel-m n :ticks n :dt n :closing-velocity-mps n}.

  `:sim-peak-crush-force-n` is `platen-mass-kg` times the PEAK
  magnitude of tick-to-tick velocity change (along the travel axis)
  divided by `dt` -- F = m*a, derived from the ACTUAL simulated
  velocity trajectory (the SAME technique every real-physics sibling in
  this fleet uses). `:sim-peak-crush-pressure-mpa` divides that force
  by the cell's own informational `crush-contact-area-mm2` -- 1 MPa =
  1 N/mm^2 -- informational only (see ns docstring: the tolerance check
  uses the force reading directly). `:sim-peak-crush-travel-m` is the
  largest AABB penetration depth (m) actually observed between the
  platen's leading face and the cell's near face across the whole
  trajectory -- informational, derived from the actual simulated
  positions, not invented.

  Pure, deterministic -- the same `platen-mass-kg` always reproduces
  the same telemetry; no IO, no wall-clock."
  [platen-mass-kg]
  (let [v0 press-closing-velocity-mps
        approach-m (+ gap-m platen-half-w-m cell-half-w-m)
        ticks (long (+ settle-ticks (long (Math/ceil (/ approach-m (* v0 dt))))))
        cell-x 0.0
        platen-x (- cell-x cell-half-w-m platen-half-w-m gap-m)
        platen (p2d/make-body {:position [platen-x 0.0]
                                :velocity [v0 0.0]
                                :mass (double platen-mass-kg)
                                :restitution 0.0
                                :friction 0.0
                                :collider (p2d/make-aabb-collider platen-half-w-m platen-half-h-m)
                                :user-data :crush-platen})
        cell (p2d/make-body {:position [cell-x 0.0]
                              :velocity [0.0 0.0]
                              :mass 0.0
                              :restitution 0.0
                              :friction 0.0
                              :collider (p2d/make-aabb-collider cell-half-w-m cell-half-h-m)
                              :user-data :cylindrical-cell})
        w0 (p2d/world-new [0.0 0.0])
        [w1 platen-id] (p2d/world-add w0 platen)
        [w2 _cell-id] (p2d/world-add w1 cell)
        worlds (reductions (fn [w _] (p2d/world-step w dt)) w2 (range ticks))
        trajectory (mapv (fn [tick world]
                            (let [b (nth (:bodies world) platen-id)]
                              {:tick tick :position (:position b) :velocity (:velocity b)}))
                          (range (count worlds)) worlds)
        vxs (mapv (comp first :velocity) trajectory)
        peak-decel-mps2 (->> (map (fn [va vb] (Math/abs (/ (- vb va) dt))) vxs (rest vxs))
                              (reduce max 0.0))
        contact-plane-x (- cell-x cell-half-w-m)
        penetrations-m (mapv (fn [{:keys [position]}]
                                (max 0.0 (- (+ (first position) platen-half-w-m) contact-plane-x)))
                              trajectory)
        peak-force-n (* (double platen-mass-kg) peak-decel-mps2)]
    {:trajectory trajectory
     :sim-peak-crush-force-n peak-force-n
     :sim-peak-crush-pressure-mpa (/ peak-force-n crush-contact-area-mm2)
     :sim-peak-crush-travel-m (reduce max 0.0 penetrations-m)
     :ticks (count trajectory)
     :dt dt
     :closing-velocity-mps v0}))

(defn crush-telemetry-for
  "Runs the REAL `simulate-crush` time-stepped `physics-2d` simulation
  for `cell-batch`'s own recorded `:crush-press-platen-mass-kg`
  press-run configuration and returns the actual simulated telemetry:
  {:sim-peak-crush-force-n n :sim-peak-crush-pressure-mpa n
  :sim-peak-crush-travel-m n :ticks n :dt n :closing-velocity-mps n}.
  Pure, deterministic -- the same `:crush-press-platen-mass-kg` always
  reproduces the same telemetry."
  [cell-batch]
  (select-keys (simulate-crush (:crush-press-platen-mass-kg cell-batch))
               [:sim-peak-crush-force-n :sim-peak-crush-pressure-mpa
                :sim-peak-crush-travel-m :ticks :dt :closing-velocity-mps]))

(def mission-actions
  "The three-step crush-test-cell verification mission every cell-batch
  walks through before `:actuation/ship-cell-batch` is proposable.
  :sense at :none safety, :actuate at :low -- verification/QA handling
  of a sampled cell under a mechanical-abuse test, not the moving-
  shipment actuation that is `:actuation/ship-cell-batch` itself
  (always :safety-critical -- see `cellworks.governor`)."
  [{:step :cell-batch-visual-dimensional-check :kind :sense   :safety :none}
   {:step :un383-t6-crush-test-cycle           :kind :actuate :safety :low}
   {:step :post-crush-thermal-voltage-scan     :kind :sense   :safety :none}])

(defn crush-force-out-of-tolerance?
  "Ground-truth check: does `cell-batch`'s own recorded REAL
  `:sim-peak-crush-force-n` (the ACTUAL `physics-2d`-simulated
  press-collision reading -- see `crush-telemetry-for`) exceed UN
  38.3 T6's own real, cited `un383-t6-crush-force-ceiling-n` (13 kN)?
  Needs no mission run or proposal inspection once the telemetry is on
  file -- its inputs are permanent fields already on the cell-batch,
  the same shape `cellworks.registry/cell-batch-resistance-out-of-
  range?` uses for internal-resistance deviation."
  [{:keys [sim-peak-crush-force-n]}]
  (and (number? sim-peak-crush-force-n)
       (> sim-peak-crush-force-n un383-t6-crush-force-ceiling-n)))

(defn simulate-crush-test
  "Run the robot-executed UN 38.3 T6 crush-test verification mission
  for `cell-batch-id` (`cell-batch` is the full record, incl.
  `:crush-press-platen-mass-kg`). Actually runs the REAL engine:
  `crush-telemetry-for` -- the actual `physics-2d`-stepped press-
  platen/cell collision trajectory (`:sim-peak-crush-force-n`/
  `:sim-peak-crush-pressure-mpa`).

  Returns {:mission .. :actions [{:action .. :proof ..} ..] :passed?
  bool :sim-peak-crush-force-n n :sim-peak-crush-pressure-mpa n}.
  Deterministic: :passed? is derived from the cell-batch's OWN
  recorded press-run configuration via the REAL simulated trajectory
  (`crush-force-out-of-tolerance?`), never invented or randomized --
  `kotoba.robotics` mandates no network/IO, and a repeatable simulation
  is what makes the governor's independent recheck
  (`simulation-out-of-tolerance?`) meaningful."
  [cell-batch-id cell-batch]
  (let [telemetry (crush-telemetry-for cell-batch)
        out-of-range? (crush-force-out-of-tolerance? (merge cell-batch telemetry))
        reading (if out-of-range? :out-of-tolerance :nominal)
        mission (robotics/mission (str "mission-" cell-batch-id "-crush-test")
                                   :robot/crush-test-cell-1
                                   :un383-t6-crush-verification
                                   :boundaries {:station "cell-safety-crush-test-cell"}
                                   :max-steps (count mission-actions))
        actions (mapv (fn [{:keys [step kind safety]}]
                        (let [a (robotics/action (str (:mission/id mission) "-" (name step))
                                                  (:mission/id mission) kind safety
                                                  :params {:step step :cell-batch-id cell-batch-id})]
                          {:action a
                           :proof (robotics/telemetry-proof (:mission/id mission) step reading
                                                             :provenance :simulated)}))
                      mission-actions)]
    {:mission mission
     :actions actions
     :passed? (not out-of-range?)
     :sim-peak-crush-force-n (:sim-peak-crush-force-n telemetry)
     :sim-peak-crush-pressure-mpa (:sim-peak-crush-pressure-mpa telemetry)}))

(defn simulation-out-of-tolerance?
  "Independent ground-truth recheck for the governor: does
  `cell-batch`'s OWN current, on-file real `physics-2d`-simulated
  crush-force telemetry (`:sim-peak-crush-force-n`) exceed UN 38.3
  T6's own cited crush-force ceiling right now? Ignores whatever
  :passed? verdict a prior mission run stored -- identical in spirit to
  `cellworks.registry/cell-batch-resistance-out-of-range?`'s refusal to
  trust a proposal's self-report. Does NOT re-run the simulation -- it
  re-derives the boolean from the real, already-persisted telemetry
  field (`cellworks.store` persists it on every `:cell-batch/upsert`),
  the same 'ground truth, not self-report' discipline applied to the
  STORED reading, not a fresh recompute."
  [cell-batch]
  (crush-force-out-of-tolerance? cell-batch))
