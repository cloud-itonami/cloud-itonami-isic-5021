# Governance

`cloud-itonami-isic-5021` is an OSS open-business blueprint for inland
passenger water transport dispatch/scheduling operations coordination
(ISIC Rev.5 5021 -- river ferries, lake excursion boats, canal passenger
boats).

## Maintainers
Maintainers may merge changes that preserve these invariants:
- a proposal for an unverified/unregistered route, or a maintenance
  order naming an unverified/unregistered contractor, can never commit.
- the FerryDispatchGovernor remains independent of the advisor.
- hard policy violations (non-`:propose` effect, vessel-seaworthiness-
  clearance/passenger-capacity/captain-fitness-finalization content, an
  op outside the closed allowlist) cannot be overridden by human
  approval.
- every service-record log, crossing-schedule proposal, maintenance-
  order coordination and safety-concern flag is auditable.
- this actor NEVER directly operates a vessel or overrides a captain's
  safety judgment, and it never has any op that could authorize
  exceeding a certified passenger-capacity limit.
- passenger, crew and vessel data stays outside Git.

## Decision Records
Architecture decisions live in `docs/adr/`. Changes to the trust model,
storage contract, public business model, operator certification or
license should add or update an ADR.

## Operator Governance
Anyone may fork and operate independently. itonami.cloud certification is
a separate trust mark and should require security, audit and data-flow
review.

Certified operators can lose certification for:
- bypassing service-record, crossing-schedule, maintenance-order or
  safety-concern policy checks
- mishandling passenger, crew or vessel data
- misrepresenting certification status
- failing to respond to security or safety incidents
