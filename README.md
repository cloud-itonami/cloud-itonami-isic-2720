# cloud-itonami-isic-2720

Open Business Blueprint for **ISIC Rev.5 2720**: manufacture of
batteries and accumulators -- lithium-ion cell/pack-batch intake,
per-scheme battery-safety-certification evidence verification,
end-of-line internal-resistance/capacity-fade quality screening, robot
UN 38.3 T6 mechanical crush-test simulation and Battery Safety Test
Report finalization for a community battery-cell/pack plant.

This repository publishes a battery-cell/pack manufacturing actor --
cell-batch intake, per-scheme lithium-battery safety-certification
evidence-checklist verification, end-of-line internal-resistance/
capacity-fade defect screening, robot mechanical crush-test mission and
Battery Safety Test Report issuance -- as an OSS business that any
qualified battery-cell/pack plant can fork, deploy, run, improve and
sell, so a plant keeps its own production and safety-conformance
history instead of renting a closed MES / quality SaaS.

Built on this workspace's
[`langgraph`](https://github.com/kotoba-lang/langgraph)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, Datomic/in-mem checkpoints) -- the same actor pattern as
every prior actor in this fleet -- here it is **Cell Advisor ⊣
Cell-Safety Governor**.

## Scope note: the missing upstream stage for BOTH smartphones and EVs

This repository is scoped to **manufacturing lithium-ion battery
cells/cell-batches** -- crush-test-verified, safety-certified cells
released as a batch to a downstream device or vehicle assembler. It is
not a device-assembly or vehicle-assembly vertical itself. Battery
cell/pack manufacturing sits directly UPSTREAM of both:

- `cloud-itonami-isic-2630` -- manufacture of communication equipment
  (smartphone/communication-device assembly). Every smartphone needs a
  battery pack upstream of final device assembly; `commsdevice.
  robotics`'s own display-bonding-press mission runs downstream of
  THIS actor's `:actuation/ship-cell-batch` hand-off.
- `cloud-itonami-isic-2910` -- manufacture of motor vehicles (final
  assembly) / `cloud-itonami-isic-2920` -- manufacture of bodies
  (coachwork) for motor vehicles. Every battery-electric (and
  fuel-cell-hybrid) vehicle needs a battery pack upstream of powertrain
  integration -- `kami-engine-vehicle-designer`'s own `vdesign.
  powertrain`/`vdesign.design` already model a BEV/FCEV's own
  `:energy`/`:store-mass-kg` fields, which THIS actor's cell-batches
  are the real upstream input for.

This vertical is the natural UNIFYING upstream stage for both chains:
neither a smartphone nor a BEV can assemble without a safety-certified
battery cell-batch first passing THIS actor's crush-test and
safety-certification gates. Distinct from:

- `cloud-itonami-isic-2630` -- device ASSEMBLY (consumes cell-batches,
  does not produce them).
- `cloud-itonami-isic-2910`/`cloud-itonami-isic-2920` -- vehicle/body
  ASSEMBLY (consumes cell-batches for BEV/FCEV powertrain integration,
  does not produce them).
- `cloud-itonami-isic-2410` -- basic iron/steel manufacturing (an
  unrelated raw-material vertical; battery cells consume different raw
  materials -- lithium/cobalt/nickel/graphite chemistries -- not
  covered here as a raw-material vertical either).

## Upstream -> downstream hand-off (2720 -> 2630 / 2910 / 2920)

```text
cloud-itonami-isic-2720 (THIS repo: cell-batch crush-test + safety-cert -> released cell-batch)
  --> cloud-itonami-isic-2630 (smartphone/communication-device assembly: battery pack integration)
  --> cloud-itonami-isic-2910 / cloud-itonami-isic-2920 (motor-vehicle/body assembly: BEV/FCEV powertrain integration)
```

`:actuation/ship-cell-batch` is the REAL hand-off event: a battery
plant dispatches a crush-test-verified, safety-certified cell-batch
onward to a downstream device or vehicle assembler. This actor does
not assume which downstream consumer a given cell-batch ships to --
the same released cell-batch record and Battery Safety Test Report
serve either hand-off.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical domain work**. Here robots (crush-test-cell
handling, end-of-line internal-resistance/capacity-fade scan) operate
under an actor that proposes actions and an independent **Cell-Safety
Governor** that gates them. The governor never issues a Battery Safety
Test Report itself; `:high`/`:safety-critical` actions (`:actuation/
ship-cell-batch`, `:actuation/issue-safety-certificate`) require human
sign-off.

**Robot process simulation is a REAL, time-stepped physics
simulation, not a symbolic field comparison** (native from day one,
per ADR-2607151600/ADR-2607152000's fleet pattern -- this vertical is a
NEW actor built to that standard, not a retrofit): `cellworks.robotics`
walks every cell-batch through a robot-executed UN 38.3 T6
("Impact/Crush") mechanical crush-test verification mission
(`kotoba.robotics` mission/action/telemetry-proof contracts) -- a
real, tested rigid-body physics engine (`kotoba-lang/physics-2d`)
time-steps a press-platen rigid body closing at a controlled velocity
onto a static cylindrical-cell rigid body (crushing perpendicular to
the cell's longitudinal axis, literally matching UN 38.3 T6's own
specified crush orientation), and reads a real peak crush force/
pressure (`:sim-peak-crush-force-n`/`:sim-peak-crush-pressure-mpa`,
Newtons/MPa) directly off the simulated collision -- not an invented
or hand-set number. The Cell-Safety Governor independently re-derives
the cell-batch's own `:sim-peak-crush-force-n` against UN 38.3 T6's
own real, cited 13 kN crush-force ceiling (`cellworks.robotics/
un383-t6-crush-force-ceiling-n`), never trusting the mission's
self-reported verdict alone (see `cellworks.robotics`'s own docstring
for the full honest disclosure of every engineering prior this
simulation uses, including the well-corroborated-secondary-citation
confidence level for the 13 kN figure itself).

## Core contract

```text
cell-batch intake + cell-safety-rules verify + end-of-line quality screen
  -> Cell Advisor proposal
  -> Cell-Safety Governor (HARD holds un-overridable)
  -> phase gate (actuation always escalates)
  -> human approval for high stakes
  -> append-only ledger + draft records
```

## Actuation honesty

Shipping a cell-batch onward to a downstream device/vehicle assembler
via a robot handling/dispatch action and issuing a Battery Safety Test
Report produce **unsigned draft records and ledger facts only**. This
actor does not talk to real plant control systems or a downstream
assembler's own intake portal. Signature and hardware dispatch are the
battery plant's own acts.

## Ops

| Op | Effect |
|---|---|
| `:cell-batch/intake` | normalize cell-batch directory patch (phase 3 may auto-commit when clean) |
| `:cell-safety-rules/verify` | per-scheme battery-safety-certification evidence checklist (UN 38.3 / IEC 62133-2 / UL 2054 / GB 31241; always human) |
| `:end-of-line-quality/screen` | end-of-line internal-resistance/capacity-fade defect screen (HARD hold if unresolved) |
| `:robotics/simulate-crush-test` | robot UN 38.3 T6 mechanical crush-test verification mission (always human; required on file before shipment) |
| `:actuation/ship-cell-batch` | draft cell-batch-shipment record onward to a downstream device/vehicle assembler (always human; HARD hold if robotics-sim missing, independently out-of-tolerance, or internal-resistance deviation out of range) |
| `:actuation/issue-safety-certificate` | draft Battery Safety Test Report record (always human) |

## Battery safety-certification schemes (honest coverage)

`cellworks.facts` seeds four REAL, current, cited schemes -- see that
namespace's own docstring for the full honest disclosure of why these
keys are not simple per-country codes (battery safety certification is
a mix of a global transport regime, national mandatory standards and
an international baseline standard, not a per-country statute table
like `automotive.facts`'s vehicle type-approval):

- **UN 38.3** (UN Manual of Tests and Criteria, Sub-section 38.3) --
  mandatory worldwide for the transport of ALL lithium metal/
  lithium-ion cells and batteries; T1-T8 test series (altitude
  simulation/thermal cycling/vibration/shock/external-short-circuit/
  impact-crush/overcharge/forced-discharge).
- **IEC 62133-2** -- the international baseline safety standard for
  portable sealed secondary lithium cells/batteries (adopted e.g. by
  the EU as the harmonized EN 62133-2).
- **UL 2054** -- the US pack-level battery safety standard (household
  and commercial batteries, incl. lithium-ion/lithium-polymer).
- **GB 31241** -- China's mandatory national safety standard for
  lithium-ion cells/batteries in portable electronic equipment,
  including its own crush/nail-penetration mechanical-abuse test
  suite.

A scheme not in this table (e.g. the demo's `"ATL"` jurisdiction) has
NO spec-basis and the Cell-Safety Governor HARD-holds rather than
inventing one -- see `cellworks.facts` for the full coverage
discipline.

## Social / regulatory hand-off

```clojure
(require '[cellworks.store :as store]
         '[cellworks.export :as export])

(def db (store/seed-db))
(export/audit-package db)           ;; EDN maps for downstream-assembler/safety-audit hand-off
(export/package->csv-bundle db)     ;; CSV bundle (cell-batches/ledger/shipments/safety-certificates)
```

Operator console (static sample): `docs/samples/operator-console.html`.

## Develop

```bash
clojure -M:dev:test
clojure -M:lint
clojure -M:dev:run
```

## License

AGPL-3.0-or-later — see `LICENSE`.

## Operator console (Pages)

After enabling GitHub Pages (Settings → Pages → GitHub Actions), the
static console is at:

https://cloud-itonami.github.io/cloud-itonami-isic-2720/

Local: open `docs/index.html` or `docs/samples/operator-console.html`.

## Export audit package (CLI)

```bash
clojure -M:dev:export
# or: clojure -M:dev:export /tmp/audit-2720
```

Writes CSV files under `out/audit-package/` (or the given directory).
