# Operator Guide

## First Deployment
1. Register operator, routes and maintenance contractors; independently
   confirm each route's vessel identity/operator-license status and each
   contractor's registration before seeding `ferryops.store`.
2. Import existing voyage/ridership/incident-report, crossing-schedule
   and maintenance-order history.
3. Run read-only service-record-logging and crossing-schedule dry-runs
   (Phase 0-1).
4. Configure the rollout phase and the `coordinate-maintenance-order`
   cost-escalation threshold for human sign-off paths.
5. Publish a dry-run safety-concern flag and audit export.

## Minimum Production Controls
- route-registration/verification check (vessel identity + operator
  license, independently confirmed) before ANY proposal for that route
- maintenance-contractor-registration/verification check before ANY
  `:coordinate-maintenance-order` proposal
- governor gate on every proposal before commit
- human sign-off for `:flag-safety-concern` (always) and high-cost
  `:coordinate-maintenance-order` proposals
- audit export for every commit, hold and approval
- backup manual dispatch process

## Certification
Certified operators must prove route/contractor-verification discipline,
governor-bypass resistance, evidence-backed safety-concern reporting and
human review for every escalation-gated action. Certification explicitly
excludes any claim of authority over vessel-seaworthiness clearance,
passenger-capacity limits or captain-fitness determinations -- those
remain independently certified elsewhere; this actor only coordinates
dispatch/scheduling logistics around them.
