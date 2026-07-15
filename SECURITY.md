# Security Policy

This project handles battery-cell manufacturing, safety-certification and
end-of-line quality-conformance workflows. Treat vulnerabilities as
potentially high impact even when the demo data is synthetic -- a bypassed
crush-test/safety-certification gate on real hardware is a fire/explosion
hazard, not merely a data-integrity issue.

## Do Not Disclose Publicly

Report privately before opening public issues for:

- credential exposure
- real plant, supplier or personnel data exposure
- authorization bypass
- Cell-Safety Governor bypass
- audit-ledger tampering
- over-disclosure in reports or exports
- tenant isolation failures

## Reporting

Use GitHub private vulnerability reporting when available for the repository.
If that is unavailable, contact the repository maintainers through the
cloud-itonami organization before publishing details.

Include:

- affected commit or version
- reproduction steps
- expected and actual behavior
- impact on cell-batch records, policy enforcement or audit logging
- suggested fix, if known

## Production Guidance

- Store secrets outside Git.
- Keep real plant/production data outside this repository.
- Run policy tests before deployment.
- Export and review audit logs regularly.
- Use least privilege for operators and service accounts.
