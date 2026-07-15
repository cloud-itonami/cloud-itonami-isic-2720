# Operator Guide

## First Deployment
1. Register quality engineers, plants, cell-batches, personnel and
   robots.
2. Import historical cell-batch / end-of-line / safety-certification
   records.
3. Run read-only validation and robot mission dry-runs.
4. Configure battery-safety-certification evidence checklists and
   human sign-off paths.
5. Publish a dry-run audit export.

## Minimum Production Controls
- governor gate on every robot action before dispatch
- human sign-off for `:high`/`:safety-critical` robot actions (e.g.
  mechanical crush-test on cell-batches, Battery Safety Test Report
  issuance)
- audit export for every shipment, sign-off and disclosure
- backup manual process

## Certification
Certified operators must prove robot-safety integrity, evidence-backed
records and human review for safety-affecting actions.

## Operating states
intake : cell-safety-rules-verify : end-of-line-quality-screen : robotics-simulate-crush-test : approve : ship-cell-batch : issue-safety-certificate : audit

## Audit export (social operation)

After a production session, export the append-only package for
downstream-assembler quality auditors or internal compliance:

```clojure
(require '[cellworks.store :as store]
         '[cellworks.export :as export])
(export/audit-package store)        ; EDN maps
(export/package->csv-bundle store)  ; CSV files as string map
```

Drafts remain **unsigned** — signing and shipment to the downstream
device/vehicle assembler's own intake are the battery plant's own acts
(see README Actuation honesty).

Static UI sample: `docs/samples/operator-console.html`.
