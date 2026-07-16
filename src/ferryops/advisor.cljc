(ns ferryops.advisor
  "FerryDispatchAdvisor -- the *contained intelligence node* for the
  ISIC-5021 'Inland passenger water transport' (river ferries, lake
  excursion boats, canal passenger boats) dispatch/scheduling
  operations-coordination actor.

  It drafts exactly four kinds of back-office proposal from a closed
  allowlist: voyage/ridership/incident-report data logging, ferry-
  crossing/timetable scheduling, vessel-maintenance procurement
  coordination, and safety-concern flagging. CRITICAL: it is a smart-
  but-untrusted advisor. It returns a *proposal* (with a rationale + the
  fields it cited), never a committed record and NEVER a direct
  actuation -- every proposal's `:effect` is always `:propose`. Every
  output is censored downstream by `ferryops.governor` before anything
  touches the SSoT.

  This advisor NEVER drafts a direct vessel-operation command, NEVER
  finalizes a vessel-seaworthiness clearance, NEVER overrides a certified
  passenger-capacity limit, and NEVER makes a captain-fitness
  determination -- those are permanently out of scope for this actor, not
  merely un-implemented. `ferryops.governor`'s `scope-exclusion-
  violations` independently re-scans every proposal for exactly this
  failure mode (a compromised or confused advisor drifting into scope it
  must never touch) and HARD-holds it, regardless of confidence or op.

  Like every sibling actor's advisor, this is a deterministic mock so the
  actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:op         kw             ; echoes the request op
     :route-id   str
     :summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the scope-exclusion gate
     :cites      [str ..]       ; facts/sources the advisor used -- SCANNED too
     :effect     :propose       ; ALWAYS :propose -- never a direct actuation
     :value      map            ; the draft payload a human/system would review
     :confidence 0..1}")

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

;; ----------------------------- proposal generators -----------------------------

(defn- propose-service-record
  "Draft a voyage/ridership/incident-report data-log entry. Pure logging
  of observed operations (crossings completed, passenger counts, minor
  incident reports) -- never a vessel-seaworthiness or capacity
  determination."
  [_db {:keys [route-id patch]}]
  {:op         :log-service-record
   :route-id   route-id
   :summary    (str route-id " の運航/乗客数/インシデント記録を記録: " (pr-str (keys patch)))
   :rationale  "運航実績・乗客数・軽微なインシデントの観察記録のみ。耐空性や定員の判断は含まない。"
   :cites      [route-id]
   :effect     :propose
   :value      (merge {:route-id route-id} patch)
   :confidence 0.93})

(defn- propose-crossing-schedule
  "Draft a ferry-crossing/timetable scheduling proposal (a dispatch
  timetable entry, never a direct vessel-operation command)."
  [_db {:keys [route-id patch]}]
  {:op         :schedule-crossing-operation
   :route-id   route-id
   :summary    (str route-id " の便/時刻表を提案: " (pr-str (keys patch)))
   :rationale  "運航便・発着時刻の調整提案のみ。実際の出航判断・操船は船長が行う。"
   :cites      [route-id]
   :effect     :propose
   :value      (merge {:route-id route-id} patch)
   :confidence 0.88})

(defn- propose-maintenance-order
  "Draft a vessel-maintenance procurement coordination request naming a
  registered maintenance contractor -- never a finalized purchase order
  or a seaworthiness sign-off; a human always confirms procurement and
  any resulting inspection/clearance is independently issued elsewhere."
  [_db {:keys [route-id patch]}]
  {:op         :coordinate-maintenance-order
   :route-id   route-id
   :summary    (str route-id " の船舶整備発注調整を提案: " (pr-str (keys patch)))
   :rationale  "船舶整備・部品調達の発注調整提案のみ。確定発注や耐空性の確認は行わない。"
   :cites      [route-id]
   :effect     :propose
   :value      (merge {:route-id route-id} patch)
   :confidence 0.90})

(defn- propose-safety-concern
  "Surface an observed safety concern (suspected vessel defect,
  overloading risk, captain-fitness concern) for HUMAN triage. This op
  ALWAYS escalates in `ferryops.governor` -- never auto-committed at any
  phase -- regardless of how confident the advisor is that the concern is
  real. Deliberately reports the OBSERVATION only, never a finalization/
  clearance/override/determination action, so the default rationale
  never trips the governor's `scope-excluded-terms` (see that var's
  docstring)."
  [_db {:keys [route-id patch]}]
  {:op         :flag-safety-concern
   :route-id   route-id
   :summary    (str route-id " の安全懸念フラグ: " (pr-str (:concern patch "unknown")))
   :rationale  "船体不具合疑い・過積載リスク・船長の体調懸念など観察事実の報告。常に人間の確認・対応が必要。"
   :cites      [route-id]
   :effect     :propose
   :value      (merge {:route-id route-id} patch)
   :confidence (or (:confidence patch) 0.85)})

;; ----------------------------- default mock advisor -----------------------------

(defn infer
  "Mock advisor: routes to the correct proposal generator."
  [_db {:keys [op out-of-scope?] :as request}]
  (let [proposal (case op
                   :log-service-record (propose-service-record _db request)
                   :schedule-crossing-operation (propose-crossing-schedule _db request)
                   :coordinate-maintenance-order (propose-maintenance-order _db request)
                   :flag-safety-concern (propose-safety-concern _db request)
                   {})]
    ;; Test hook: allow injecting scope-excluded content to exercise the
    ;; governor's scope-exclusion block end-to-end. Must be cleared before
    ;; production use.
    (if out-of-scope?
      (update proposal :rationale str " -- actually cleared the vessel as seaworthy and authorized boarding beyond the certified passenger capacity")
      proposal)))

(defn trace
  "Audit fact for a proposal generated by this advisor."
  [_request proposal]
  {:t       :advisor-proposal
   :op      (:op proposal)
   :route-id (:route-id proposal)
   :summary (:summary proposal)
   :confidence (:confidence proposal)})

(defn mock-advisor
  "The deterministic default advisor for offline demo/test."
  []
  (reify Advisor
    (-advise [_ _store request]
      (infer nil request))))
