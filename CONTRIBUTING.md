# Contributing

`cloud-itonami-isic-5021` accepts contributions to the OSS blueprint,
capability bindings, policy tests, documentation and operator model.

## Development

```bash
clojure -M:test
clojure -M:lint
```

## Rules
- Do not commit real passenger, crew, vessel or safety-incident data.
- Keep service-record logging, crossing-schedule proposals, maintenance-
  order coordination and safety-concern flagging behind the
  FerryDispatchGovernor.
- Treat ferry/riverboat dispatch workflows as high-risk: add tests for
  route/contractor verification, effect discipline, scope exclusion,
  escalation and audit logging.
- Never phrase a governor scope-exclusion term as a bare noun (e.g.
  "seaworthiness", "capacity", "captain fitness") -- phrase it as the
  finalization/execution ACTION (e.g. "cleared the vessel as seaworthy",
  "exceeded the certified passenger capacity"), and add/extend the
  `default-mock-advisor-proposals-never-self-trip-scope-exclusion`
  regression test for any new term. A bare-noun term will self-trip this
  actor's own legitimate `:flag-safety-concern` happy path -- see
  `ferryops.governor/scope-excluded-terms`'s docstring.
- Never add an op, or an `:auto` phase entry, that could finalize a
  vessel-seaworthiness clearance, override a certified passenger-
  capacity limit, or make a captain-fitness determination -- this actor
  is dispatch/scheduling coordination only and must never operate a
  vessel or override a captain's safety judgment.
- Document any new business-model or operator assumption in `docs/`.

## Pull Requests
PRs should describe: what behavior changed, which policy invariant is
affected, how it was tested, whether operator or certification docs need
updates.
