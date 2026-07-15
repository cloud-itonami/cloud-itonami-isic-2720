# ADR-0001: Cell Advisor ⊣ Cell-Safety Governor architecture

- Status: Accepted (2026-07-15)
- Repository: `cloud-itonami-isic-2720` (ISIC Rev.5 `2720`)

## Context

Lithium-ion battery cell/pack manufacturing (mechanical crush-test
verification, per-scheme battery-safety-certification evidence
verification, end-of-line internal-resistance/capacity-fade
inspection, Battery Safety Test Report issuance) needs the same
governed-actor pattern as the rest of the cloud-itonami fleet: an
untrusted advisor proposes; an independent governor may HOLD;
high-stakes actuation never auto-commits.

The industry-registry entry for `2720` had sat at `:maturity :spec`
placeholder (`gftdcojp/cloud-itonami-C2720`) with no repo, no business
model, no actor. A 2026-07-15 value-chain review found `cloud-itonami-
isic-2630` (communication-equipment/smartphone assembly) and
`cloud-itonami-isic-2910`/`cloud-itonami-isic-2920` (motor-vehicle/body
assembly) all implemented, but the battery cell/pack manufacturing
stage directly upstream of BOTH -- the stage that produces the safety-
certified cell-batches both a smartphone and a BEV/FCEV powertrain
integration actually need -- had no actor at all: a real gap common to
two value chains, not a niche single-vertical gap (see README `Scope
note`).

This vertical additionally adopts ADR-2607151600/ADR-2607152000's
real-engineering-simulation fleet pattern NATIVELY from day one (unlike
the 6 verticals ADR-2607152000 itself upgraded from a prior symbolic
layer) -- mirroring how `cloud-itonami-isic-2630`/`cloud-itonami-
isic-2920` were built real-physics-first.

## Decision

1. Namespaces live under `cellworks.*` with the standard facts /
   registry / store / governor / phase / advisor / operation / sim /
   robotics / export shape.
2. Entity is a **cell-batch** (a manufactured lot of battery cells, or
   a pack-batch), not a finished device, a finished vehicle, or a raw
   material.
3. Dual actuation on the same entity:
   - `:actuation/ship-cell-batch` (robot cell-batch-shipment dispatch
     draft, onward to a downstream device/vehicle assembler -- the
     real upstream hand-off to BOTH `cloud-itonami-isic-2630`'s
     device-unit assembly and `cloud-itonami-isic-2910`/`cloud-
     itonami-isic-2920`'s vehicle/body assembly)
   - `:actuation/issue-safety-certificate` (Battery Safety Test Report
     draft, a UN 38.3/IEC 62133-style document)
4. Double-actuation guards use dedicated booleans
   (`:cell-batch-shipped?`, `:safety-certified?`), never a status
   lifecycle (ADR-2607071320 / 6492 lesson).
5. `cell-batch-resistance-out-of-range?` continues the fleet
   two-sided range check family (after testlab / conservation / water
   / steelworks / turbine / automotive / autoparts / bodyshop),
   applied here to a cell-batch's own measured end-of-line internal-
   resistance deviation against its own recorded acceptance-band
   bounds -- a real electrical end-of-line QA metric, distinct from
   the physics-derived crush-force check.
6. `cellworks.robotics` delivers a REAL, time-stepped `physics-2d`
   rigid-body UN 38.3 T6 ("Impact/Crush") mechanical crush-test
   simulation from day one (not a symbolic field comparison, and not a
   retrofit): a press-platen `Body2D` closes at a controlled velocity
   onto a static cylindrical-cell `Body2D` (crush direction
   perpendicular to the cell's longitudinal axis, literally matching
   the standard's own specified orientation; crush-travel distance
   literally matches the standard's own 50%-deformation stopping
   criterion); `:sim-peak-crush-force-n`/`:sim-peak-crush-pressure-
   mpa` are read directly off the actual simulated collision
   trajectory. The governor HARD-holds if the mission never ran, OR if
   an independent recompute of the cell-batch's own `:sim-peak-crush-
   force-n` exceeds UN 38.3 T6's own real, cited 13 kN crush-force
   ceiling -- never trusting the mission's self-reported verdict.
7. Battery safety-certification scheme catalog (`cellworks.facts`)
   seeds UN 38.3 (global transport) / UL 2054 (USA) / GB 31241 (China)
   / IEC 62133-2 (international baseline) only; missing schemes are
   uncovered, never fabricated -- see that ns's own docstring for why
   these keys are not a simple per-country table like `automotive.
   facts`'s vehicle type-approval.
8. End-of-line defect (internal-resistance/capacity-fade) unresolved
   is evaluated unconditionally so `:end-of-line-quality/screen` itself
   can HARD-hold (parksafety ADR-2607071922 Decision 5 discipline,
   same as `automotive.governor`'s end-of-line-defect-unresolved check
   / `bodyshop.governor`'s weld-quality-defect-unresolved check).
9. GB 31241's own crush/nail-penetration test suite is cited honestly
   as real and current, but this repo does NOT fabricate a distinct
   GB 31241 numeric crush-force/displacement threshold of its own --
   `cellworks.robotics`'s tolerance check anchors ONLY on UN 38.3 T6's
   real, cited 13 kN figure (see that ns's docstring for the
   confidence disclosure).

## Consequences

(+) The battery cell/pack manufacturing stage gains a forkable OSS
operating stack with auditable governor holds, closing a gap common to
BOTH the smartphone-assembly and vehicle-assembly value chains the
2026-07-15 value-chain review identified.
(+) Delivers a REAL time-stepped physics simulation (not a symbolic
comparison) as a native part of this actor's initial build, extending
ADR-2607151600/ADR-2607152000's fleet pattern to a NEW actor rather
than retrofitting an existing symbolic one -- and, unlike prior
siblings' own disclosed-analog thresholds, anchors its tolerance
ceiling on a real, standard-specified numeric value (UN 38.3 T6's 13
kN) rather than a newly-defined proxy multiple.
(+) Genuine dual-downstream hand-off value: the same cell-batch-
shipment/safety-certificate shape serves both `cloud-itonami-
isic-2630` and `cloud-itonami-isic-2910`/`cloud-itonami-isic-2920`
without this actor needing to know which downstream consumer a given
shipment goes to.
(−) No physical plant digital-twin tick beyond the single crush-test
physics check in this repo (follow-up domain data, e.g. thermal-
runaway/venting simulation, is out of scope here -- `physics-2d` has
no chemistry/thermal model at all).
(−) Battery safety-certification-scheme coverage is a starting catalog
(4 schemes), not exhaustive, and does not capture every jurisdiction's
own supplementary requirements or cell-format-specific exemptions
(e.g. UN 38.3's small-cell/button-cell provisions).
(−) `physics-2d` is a 2D projection with no material-stiffness/
deformation model, and the modeled cell's real cylindrical cross-
section is approximated as an AABB bounding box (a disclosed
simplification necessitated by `physics-2d`'s narrowphase, which has
no mixed AABB/circle collision support) -- see `cellworks.robotics`'s
own docstring for the full disclosure, including why this simulation's
closing velocity is a disclosed analog rate rather than UN 38.3's own
literal (much slower) controlled crush-test speed.

## Related

- ADR-2607011000 (robotics premise + ISIC coverage)
- ADR-2607111600 (isic-2910 motor-vehicle promotion -- sibling
  architecture this repo mirrors)
- ADR-2607151600 (real engineering-simulation integration, automotive
  pilot)
- ADR-2607152000 (real engineering-simulation fleet extension)
- Superproject fleet ADR for this promotion: `90-docs/adr/2607160500-
  cloud-itonami-isic-2720-battery.md`
- Sibling architecture: `cloud-itonami-isic-2630` docs/adr/0001,
  `cloud-itonami-isic-2920` docs/adr/0001
