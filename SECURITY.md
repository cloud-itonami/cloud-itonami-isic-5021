# Security Policy

This project handles inland-passenger-water-transport dispatch/
scheduling and safety-concern workflows. Treat vulnerabilities as
potentially high impact even when the demo data is synthetic --
passenger-safety-adjacent domains have a historically severe failure
mode (overloading, capsizing).

## Do Not Disclose Publicly

Report privately before opening public issues for:

- credential exposure
- real passenger, crew or vessel data exposure
- authorization bypass
- FerryDispatchGovernor bypass
- audit-ledger tampering
- over-disclosure in safety-concern reports or exports
- tenant isolation failures

## Reporting

Use GitHub private vulnerability reporting when available for the repository.
If that is unavailable, contact the repository maintainers through the
cloud-itonami organization before publishing details.

Include:

- affected commit or version
- reproduction steps
- expected and actual behavior
- impact on passenger/crew/vessel data, policy enforcement or audit logging
- suggested fix, if known

## Production Guidance

- Store secrets outside Git.
- Keep real passenger, crew and vessel data outside this repository.
- Run policy tests before deployment.
- Export and review audit logs regularly.
- Use least privilege for operators and service accounts.
- Never wire this actor's output directly to a vessel-operation,
  seaworthiness-clearance, passenger-capacity-override or captain-
  fitness-determination system -- those are permanently out of scope and
  must remain independently certified elsewhere.
