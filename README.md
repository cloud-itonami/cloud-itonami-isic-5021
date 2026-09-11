# cloud-itonami-isic-5021

Open Business Blueprint for **ISIC Rev.5 5021**: inland passenger water
transport -- river ferries, lake excursion boats and canal passenger
boats.

This repository publishes an inland-passenger-water-transport
dispatch/scheduling operations-COORDINATION actor -- voyage/ridership/
incident-report data logging, ferry-crossing/timetable scheduling,
vessel-maintenance procurement coordination with a registered
maintenance contractor, and safety-concern flagging -- as an OSS
business that any qualified operator can fork, deploy, run, improve and
sell, so an independent ferry/riverboat operator never surrenders its
dispatch data to a closed back-office SaaS.

Built on this workspace's
[`langgraph`](https://github.com/kotoba-lang/langgraph)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, in-mem/Datomic checkpoints) -- the same actor pattern as
every prior actor in this fleet -- here it is **FerryDispatchAdvisor
⊣ FerryDispatchGovernor**. This blueprint's own
`:itonami.blueprint/governor` keyword, `:ferry-dispatch-governor`, is a
distinct, independent build (checked against the fleet's registered
governor keywords, including sibling ISIC 5012 maritime freight and ISIC
4719/4711 retail governors, before naming -- no collision).

> **Why an actor layer at all?** An LLM is great at drafting a
> service-record summary, a crossing-schedule proposal, or a
> maintenance-order request -- but it has no license to actually finalize
> a vessel-seaworthiness clearance, no way to independently confirm a
> route's vessel/operator-license is actually registered and verified or
> that a named maintenance contractor is actually a registered/verified
> counterparty, and no notion of when a "flag this concern" op quietly
> turns into a claim to have already overridden the passenger-capacity
> limit or determined a captain fit for duty. Letting it act directly
> invites an unverified route's data entering the ledger, an unverified
> contractor receiving a maintenance order, or -- worst of all -- a
> fabricated claim to have cleared an unsafe vessel or authorized
> boarding beyond its certified capacity, exposing passengers to real
> harm. This project seals the FerryDispatchAdvisor into a single node
> and wraps it with an independent **FerryDispatchGovernor**, a human
> **approval workflow**, and an immutable **audit ledger**.

## Scope: dispatch/scheduling coordination only, not vessel operation

This actor is **operations coordination only**. It never performs or
authorizes:

- directly operating a vessel or overriding a captain's safety judgment
- directly finalizing a vessel-seaworthiness clearance
- overriding or exceeding a certified passenger-capacity limit
- making a captain-fitness determination

The governor's `scope-exclusion-violations` check re-scans every
proposal for this failure mode independently of the advisor's own
framing, and treats it as a HARD, permanent block regardless of
confidence or how clean everything else is. Flagging a safety concern
for a human to triage is exactly this actor's job --
`:flag-safety-concern` is never excluded by this check, only
FINALIZING/overriding/determining that concern is, and this op can never
be in any phase's `:auto` set -- it always escalates to a human.

### Actuation

**Every proposal this actor generates is `:effect :propose`, never a
direct actuation.** Two independent layers enforce this
(`ferryops.governor`'s `effect-not-propose-violations` HARD check and
`ferryops.phase`'s phase table, which never puts `:flag-safety-concern`
in any phase's `:auto` set). A human dispatch coordinator is always the
one who actually acts on a flagged concern or confirms a high-cost
maintenance order.

## The core contract

```
route/vessel/operator-license registration + dispatch-coordination request
        |
        v
   ┌───────────────────────┐   proposal      ┌────────────────────────────┐
   │ FerryDispatch-        │ ─────────────▶ │ FerryDispatchGovernor       │  (independent system)
   │ Advisor (sealed)      │  + citations    │ route-unverified ·          │
   └───────────────────────┘                 │ contractor-unverified (NEW)·│
          │                 commit ◀┼ effect-not-propose ·               │
          │                         │ scope-excluded (seaworthiness/      │
    record + ledger        escalate ┼ capacity/captain-fitness            │
          │              (ALWAYS for│ finalization) ·                     │
          │       :flag-safety-     │ op-not-allowed                      │
          │       concern/high-cost └────────────────────────────┘
          │       maintenance order
          ▼
      human approval
```

**The FerryDispatchAdvisor never commits a proposal the
FerryDispatchGovernor would reject, and a safety-concern flag or a
high-cost maintenance order never commits without a human sign-off.**
Hard violations (an unregistered/unverified route; an
unregistered/unverified maintenance contractor; a non-`:propose` effect;
content touching seaworthiness-clearance/passenger-capacity/captain-
fitness finalization; an op outside the closed allowlist) force **hold**
and *cannot* be approved past.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
may perform physical domain work** (here: dock-side gangway/mooring
assistance, ticketing kiosks, telemetry sensor upkeep) under human/robot
operations gated by operator policy. This actor itself does not dispatch
robot/hardware actions or operate any vessel -- it is strictly the
dispatch/scheduling-coordination layer (service-record logging,
crossing-schedule proposals, maintenance-order coordination,
safety-concern flagging) any physical-dispatch layer could eventually
feed proposals into, always gated the same way by the independent
FerryDispatchGovernor.

## Features

- **Closed proposal-op allowlist**: `log-service-record`,
  `schedule-crossing-operation`, `coordinate-maintenance-order`,
  `flag-safety-concern` (all `:effect :propose`).
- **Four HARD governor checks** (permanent, un-overridable):
  1. **Route unverified** -- the target route's vessel identity and
     operator-license status must exist AND be independently
     registered/verified in the store.
  2. **Contractor unverified** (FLAGSHIP NEW) -- for
     `:coordinate-maintenance-order` only, the named vessel-maintenance
     contractor must exist AND be independently registered/verified -- a
     vessel-maintenance counterparty-verification gate no sibling 50xx
     actor has had reason to add.
  3. **Effect is :propose** -- any other `:effect` value is rejected.
  4. **Scope exclusion** -- directly finalizing a vessel-seaworthiness
     clearance, overriding/exceeding a certified passenger-capacity
     limit, making a captain-fitness determination, and an op outside
     the closed allowlist are all permanently blocked.
- **Two ESCALATE (SOFT) gates**, either forces human sign-off:
  - `:flag-safety-concern` -- ALWAYS escalates, regardless of confidence
    or phase. A "flag a concern" op is never auto-commit eligible and
    never finalizes a safety decision itself -- it only surfaces the
    concern for a human. This actor coordinates SCHEDULING/DISPATCH
    LOGISTICS ONLY and never directly operates a vessel or overrides a
    captain's safety judgment.
  - `:coordinate-maintenance-order` above a cost threshold -- a
    large-value procurement proposal always needs a human sign-off.
  - (LLM confidence below the floor also escalates, as with every
    sibling actor.)
- **Staged rollout** (Phase 0→3):
  - Phase 0: read-only
  - Phase 1: service-record logging only (approval-gated)
  - Phase 2: + crossing-schedule proposals, maintenance-order proposals
    (approval-gated)
  - Phase 3: auto-commits clean, high-confidence, low-cost proposals
    (safety concerns and high-cost maintenance orders always escalate)
- **Append-only audit ledger** -- every decision is an immutable log
  entry.
- **langgraph-clj StateGraph** -- one request = one supervised run;
  human-in-the-loop via `interrupt-before`.

### Development

```bash
# Install dependencies (if inside the superproject, use :dev alias for local overrides)
clojure -M:dev -P

# Run tests
clojure -M:test

# Run linter
clojure -M:lint

# Run demo
clojure -M:run
```

### Test suite

- `test/ferryops/governor_test.cljk` -- unit tests of governor hard
  checks, scope exclusion, and the self-trip regression test
- `test/ferryops/advisor_test.cljk` -- advisor proposal shape and
  consistency
- `test/ferryops/phase_test.cljk` -- rollout phase logic
- `test/ferryops/governor_contract_test.cljk` -- full graph integration,
  audit trail
- `test/ferryops/store_contract_test.cljk` -- Store protocol and MemStore
  implementation

### Modules

- `ferryops.store` -- SSoT (MemStore, String-keyed route/contractor
  directories, append-only ledger)
- `ferryops.advisor` -- contained intelligence node (mock + real-LLM
  seam)
- `ferryops.governor` -- independent compliance layer
- `ferryops.phase` -- staged rollout (0→3)
- `ferryops.operation` -- langgraph-clj StateGraph
- `ferryops.sim` -- demo driver

## Capability layer

This blueprint resolves its technology stack via
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry) (ISIC
`5021`).

## Business-process coverage (honest)

| Covered | Not covered (out of scope for this R0) |
|---|---|
| Voyage/ridership/incident-report data logging (`:log-service-record`) | Real ticketing/AIS-telemetry-system integration |
| Ferry-crossing/timetable scheduling coordination (`:schedule-crossing-operation`) | Direct vessel-operation command or captain override |
| Vessel-maintenance procurement coordination with a registered, verified contractor, HARD-gated on contractor verification and a double-actuation-free single-proposal shape (`:coordinate-maintenance-order`) | Real drydock/parts-ordering-system integration |
| Safety-concern flagging, ALWAYS human-gated (`:flag-safety-concern`) | Directly finalizing a vessel-seaworthiness clearance, overriding a certified passenger-capacity limit, or making a captain-fitness determination -- permanently out of scope, not a gap |
| Immutable audit ledger for every log/schedule/order/flag decision | Real-time vessel telemetry/positioning integration -- a follow-up slice, not in this R0 |

Extending coverage is additive: add the next op (e.g. a berth-assignment
or a weather-hold-advisory check) as its own governed op with its own
HARD checks and tests, following the SAME "an independent governor
re-verifies against the actor's own records before any real-world act"
pattern this repo's flagship checks already establish.

## Maturity

`:implemented` -- `FerryDispatchAdvisor` + `FerryDispatchGovernor` run as
real, tested code (see `Development` above), following the SAME
governed-actor architecture as every prior actor across this fleet, with
its own distinct, independently-named governor and its own novel
vessel-maintenance-contractor-verification check.

## License

Code and implementation templates are AGPL-3.0-or-later.
