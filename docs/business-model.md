# Business Model: Inland Passenger Water Transport Dispatch Coordination

## Classification
- Repository: `cloud-itonami-isic-5021`
- ISIC Rev.5: `5021` -- inland passenger water transport (river ferries,
  lake excursion boats, canal passenger boats)
- Social impact: passenger safety, public transit, rural connectivity

## Customer
- independent ferry/riverboat operators needing an auditable
  dispatch/scheduling-coordination platform
- multi-route operators needing consistent crossing-schedule/
  maintenance-order/safety-concern governance across routes
- programs that cannot accept closed, unauditable back-office platforms

## Offer
- voyage/ridership/incident-report data logging
- ferry-crossing/timetable scheduling coordination
- vessel-maintenance procurement coordination with registered, verified
  contractors
- safety-concern flagging (vessel defects, overloading risk,
  captain-fitness concerns) for human triage
- role-based access and immutable audit ledger

## Revenue
- self-host setup fee
- managed hosting subscription per operator/route
- support retainer with SLA

## Trust Controls
- `:ferry-dispatch-governor` never lets a proposal for an
  unregistered/unverified route, or a maintenance order naming an
  unregistered/unverified contractor, commit or even escalate
- every proposal's `:effect` must be `:propose` -- a claim to directly
  actuate is a HARD, un-overridable block
- directly finalizing a vessel-seaworthiness clearance, overriding a
  certified passenger-capacity limit, or making a captain-fitness
  determination is permanently out of scope, not a rollout milestone --
  the actor may only flag a concern for a human
- a `:flag-safety-concern` proposal, and a high-cost
  `:coordinate-maintenance-order`, always require human sign-off
- this actor coordinates SCHEDULING/DISPATCH LOGISTICS ONLY -- it never
  directly operates a vessel or overrides a captain's safety judgment
- sensitive passenger, crew and vessel data stays outside Git
