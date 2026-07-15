# Contributing

`cloud-itonami-isic-2720` accepts contributions to the OSS blueprint,
capability bindings, policy tests, documentation and operator model.

## Development
The capability layer lives in `kotoba-lang/*` libraries. This repo holds the
business blueprint and operator contracts.

```bash
clojure -M:dev:test
clojure -M:lint
```

## Rules
- Do not commit real operating, personal or credential data.
- Keep robot dispatch, records and disclosures behind the Cell-Safety Governor.
- Treat workflows as high-risk: add tests for robot-safety gating,
  record integrity, disclosure and audit logging.
- Document any new business-model or operator assumption in `docs/`.
- Never fabricate a battery-safety-standard citation (UN 38.3 / IEC 62133-2 /
  UL 2054 / GB 31241 or any other). If you are not confident of a
  jurisdiction's requirements or a numeric test threshold, leave it out and
  say so in `cellworks.facts`/`cellworks.robotics` coverage/docstrings --
  never invent one.

## Pull Requests
PRs should describe: what behavior changed, which policy invariant is
affected, how it was tested, whether operator or certification docs need
updates.
