(ns ferryops.governor
  "FerryDispatchGovernor -- the independent compliance layer that earns
  the FerryDispatchAdvisor the right to commit. The advisor has no notion
  of whether a route's vessel/operator-license is actually registered and
  independently verified, whether a named vessel-maintenance contractor
  is itself a registered/verified counterparty, whether its own proposed
  `:effect` secretly claims a direct actuation instead of a mere
  proposal, or whether it has silently drifted into a permanently
  out-of-scope decision area, so this MUST be a separate system able to
  *reject* a proposal and fall back to HOLD.

  This actor's scope is deliberately narrow -- SCHEDULING/DISPATCH
  LOGISTICS COORDINATION ONLY (voyage/ridership/incident-report data
  logging, ferry-crossing/timetable scheduling, vessel-maintenance
  procurement coordination, safety-concern flagging). It NEVER performs
  or authorizes:
    - directly operating a vessel or overriding a captain's safety
      judgment
    - directly finalizing a vessel-seaworthiness clearance
    - overriding or exceeding a certified passenger-capacity limit
    - making a captain-fitness determination

  Four HARD checks, ALL permanent, un-overridable by any human approval:

    1. Route unverified            -- the target route's vessel identity
                                       and operator-license status must
                                       exist AND be independently
                                       confirmed `:registered?`/
                                       `:verified?` in the store before
                                       ANY proposal for it may commit or
                                       even escalate. Never trusts a
                                       proposal's own claim about the
                                       route -- re-derived from the
                                       route's own record, the same
                                       'ground truth, not self-report'
                                       discipline every sibling actor's
                                       governor uses.
    2. Contractor unverified       -- for `:coordinate-maintenance-order`
                                       ONLY, the proposal's own drafted
                                       `:value` must name a
                                       `:contractor-id` that resolves to
                                       an independently
                                       `:registered?`/`:verified?`
                                       vessel-maintenance contractor
                                       record. A missing contractor-id,
                                       or one that resolves to an
                                       unregistered or unverified
                                       contractor, is a HARD block -- the
                                       flagship genuinely new check this
                                       vertical adds (a vessel-
                                       maintenance counterparty-
                                       verification gate no sibling 50xx
                                       actor has had reason to add).
    3. Effect not :propose         -- every proposal's `:effect` MUST be
                                       `:propose`. Any other effect value
                                       is, by construction, a claim to
                                       directly actuate/commit outside
                                       governance -- HARD block, not
                                       merely low-confidence.
    4. Scope exclusion             -- ANY proposal (regardless of op)
                                       whose op, summary, rationale,
                                       cites or draft value touches
                                       directly finalizing a vessel-
                                       seaworthiness clearance,
                                       overriding/exceeding a certified
                                       passenger-capacity limit, or
                                       making a captain-fitness
                                       determination is a HARD,
                                       PERMANENT block -- this actor's
                                       charter excludes that territory
                                       structurally, not as a rollout
                                       milestone. Evaluated
                                       UNCONDITIONALLY on every proposal.
                                       An op outside the closed four-op
                                       allowlist is the SAME failure mode
                                       (an advisor proposing something it
                                       was never authorized to propose)
                                       and is folded into this same
                                       check. `:flag-safety-concern`
                                       itself is never excluded by this
                                       check -- surfacing a vessel-
                                       defect/overloading-risk/captain-
                                       fitness concern for a human is
                                       exactly this actor's job; only
                                       FINALIZING/overriding/determining
                                       that concern is excluded (see
                                       `scope-excluded-terms` below --
                                       phrased as the finalization/
                                       execution ACTION, never a bare
                                       noun like 'seaworthiness',
                                       'capacity' or 'captain fitness',
                                       so the default mock advisor's own
                                       `:flag-safety-concern` rationale
                                       never self-trips this check).

  Two ESCALATE (SOFT) gates, either forces human sign-off:
    - LLM confidence below the floor.
    - The op is `:flag-safety-concern` -- ALWAYS escalates to a human,
      regardless of confidence, regardless of how clean the proposal
      otherwise is. `ferryops.phase` independently agrees:
      `:flag-safety-concern` is never a member of any phase's `:auto`
      set either -- two layers, not one.
    - A `:coordinate-maintenance-order` whose drafted `:value` names an
      `:estimated-cost` above `maintenance-cost-threshold` -- a
      large-value vessel-maintenance procurement proposal always needs a
      human sign-off, even when the governor and phase would otherwise
      allow auto-commit."
  (:require [clojure.string :as str]
            [ferryops.store :as store]))

(def confidence-floor 0.6)

(def maintenance-cost-threshold
  "Example single-vessel maintenance-procurement threshold
  (USD-equivalent units, domain-illustrative -- not a universal
  cross-domain constant). A `:coordinate-maintenance-order` proposal
  citing an `:estimated-cost` above this value ALWAYS escalates to human
  sign-off, regardless of confidence or rollout phase."
  2000.0)

(def allowed-ops
  "The closed proposal-op allowlist -- an op outside this set is a scope
  violation by construction (see `scope-exclusion-violations`)."
  #{:log-service-record :schedule-crossing-operation
    :coordinate-maintenance-order :flag-safety-concern})

(def always-escalate-ops
  "Ops that ALWAYS require human sign-off, clean or not."
  #{:flag-safety-concern})

(def scope-excluded-terms
  "Case-insensitive substrings that mark a proposal as touching a
  permanently out-of-scope decision area -- directly finalizing a
  vessel-seaworthiness clearance, overriding/exceeding a certified
  passenger-capacity limit, or making a captain-fitness determination.
  Scanned across the proposal's op/summary/rationale/cites/value, never
  trusting the advisor's own framing of its intent.

  CRITICAL: every term here is phrased as the finalization/execution
  ACTION (e.g. 'cleared the vessel as seaworthy', 'exceeded the certified
  passenger capacity'), never a bare noun like 'seaworthiness',
  'capacity', 'overloading' or 'captain fitness' -- a bare noun would
  accidentally match inside this actor's own legitimate
  `:flag-safety-concern` default proposal text (whose whole job is to
  talk about vessel-defect/overloading-risk/captain-fitness concerns) and
  self-block the happy path. See
  `ferryops.governor-test/default-mock-advisor-proposals-never-self-trip-scope-exclusion`
  for the regression test."
  ["finalize the seaworthiness clearance" "finalized the seaworthiness clearance" "finalizes the seaworthiness clearance"
   "issue the seaworthiness clearance" "issued the seaworthiness clearance" "issuing the seaworthiness clearance"
   "clear the vessel as seaworthy" "cleared the vessel as seaworthy" "clearing the vessel as seaworthy"
   "certify the vessel as seaworthy" "certified the vessel as seaworthy" "certifying the vessel as seaworthy"
   "sign off on the seaworthiness" "signed off on the seaworthiness" "signing off on the seaworthiness"
   "override the passenger capacity limit" "overrode the passenger capacity limit" "overriding the passenger capacity limit"
   "override the certified capacity limit" "overrode the certified capacity limit" "overriding the certified capacity limit"
   "exceed the certified passenger capacity" "exceeded the certified passenger capacity" "exceeding the certified passenger capacity"
   "authorize boarding beyond capacity" "authorized boarding beyond capacity" "authorizing boarding beyond capacity"
   "authorize boarding beyond the certified passenger capacity" "authorized boarding beyond the certified passenger capacity"
   "board beyond the certified capacity" "boarded beyond the certified capacity" "boarding beyond the certified capacity"
   "determine the captain fit for duty" "determined the captain fit for duty" "determining the captain fit for duty"
   "determine the captain unfit for duty" "determined the captain unfit for duty" "determining the captain unfit for duty"
   "certify the captain fit to sail" "certified the captain fit to sail" "certifying the captain fit to sail"
   "declare the captain fit to sail" "declared the captain fit to sail" "declaring the captain fit to sail"
   "declare the captain unfit to sail" "declared the captain unfit to sail" "declaring the captain unfit to sail"
   "clear the captain for duty" "cleared the captain for duty" "clearing the captain for duty"
   "耐空性証明を発行した" "耐空性証明を発行する" "耐空性を確定した" "耐空性を確定する"
   "定員上限を超えて乗船を許可した" "定員上限を超えて乗船を許可する" "定員超過での乗船を許可した"
   "船長の適性を判定した" "船長の適性を判定する" "船長を乗船可能と判定した" "船長を乗船不可と判定した"])

;; ----------------------------- checks -----------------------------

(defn- route-unverified-violations
  "The target route's vessel/operator-license status must exist AND be
  independently `:registered?`/`:verified?` in the store -- never trust
  the proposal's own `:route-id` claim without a route lookup."
  [{:keys [route-id]} st]
  (let [r (store/route-record st route-id)]
    (when-not (and r (:registered? r) (:verified? r))
      [{:rule :route-unverified
        :detail (str route-id " は未登録または未検証の航路/船舶 -- いかなる提案も進められない")}])))

(defn- contractor-unverified-violations
  "For `:coordinate-maintenance-order` ONLY, the proposal's own drafted
  `:value` must name a `:contractor-id` that resolves to an independently
  `:registered?`/`:verified?` vessel-maintenance contractor record. A
  missing contractor-id, or one that resolves to an unregistered/
  unverified contractor, is a HARD block -- never trust the proposal's
  own contractor claim without a store lookup, the SAME 'ground truth,
  not self-report' discipline as `route-unverified-violations`,
  reapplied to the vessel-maintenance counterparty."
  [proposal st]
  (when (= :coordinate-maintenance-order (:op proposal))
    (let [contractor-id (get-in proposal [:value :contractor-id])
          c (and contractor-id (store/contractor-record st contractor-id))]
      (when-not (and c (:registered? c) (:verified? c))
        [{:rule :contractor-unverified
          :detail (str (or contractor-id "(contractor-id missing)")
                        " は未登録または未検証の整備業者 -- 整備発注調整提案を進められない")}]))))

(defn- effect-not-propose-violations
  "`:effect` must ALWAYS be `:propose` -- any other value is a claim to
  directly actuate/commit outside governance."
  [proposal]
  (when (not= :propose (:effect proposal))
    [{:rule :effect-not-propose
      :detail (str ":effect は :propose のみ許可されるが " (pr-str (:effect proposal)) " が提案された")}]))

(defn- text-blob
  "Flatten every advisor-authored field on a proposal into one lower-cased
  blob the scope-exclusion scan checks."
  [proposal]
  (str/lower-case (pr-str (select-keys proposal [:op :summary :rationale :cites :value]))))

(defn- scope-exclusion-violations
  "HARD, PERMANENT block: a proposal outside the closed op allowlist, or
  one whose content touches directly finalizing a vessel-seaworthiness
  clearance, overriding/exceeding a certified passenger-capacity limit,
  or making a captain-fitness determination, regardless of confidence or
  how clean every other check is. Evaluated UNCONDITIONALLY on every
  proposal."
  [proposal]
  (let [op (:op proposal)
        blob (text-blob proposal)]
    (cond
      (not (contains? allowed-ops op))
      [{:rule :op-not-allowed
        :detail (str (pr-str op) " は許可された操作(closed allowlist)に含まれない")}]

      (some #(str/includes? blob %) scope-excluded-terms)
      [{:rule :scope-excluded
        :detail "耐空性証明の確定・定員上限超過の許可・船長適性の判定など、この actor の恒久的な対象外領域に触れる提案は永久に禁止"}])))

(defn- high-cost-maintenance-order?
  "A `:coordinate-maintenance-order` proposal citing an `:estimated-cost`
  above `maintenance-cost-threshold` -- always needs human sign-off (SOFT
  escalate, not a hard block: the order itself is in scope, only its size
  requires a human)."
  [proposal]
  (and (= :coordinate-maintenance-order (:op proposal))
       (some-> proposal :value :estimated-cost (> maintenance-cost-threshold))))

(defn check
  "Censors a FerryDispatchAdvisor proposal against the governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal store]
  (let [route-id (or (:route-id proposal) (:route-id request))
        hard (into []
                   (concat (route-unverified-violations {:route-id route-id} store)
                           (contractor-unverified-violations proposal store)
                           (effect-not-propose-violations proposal)
                           (scope-exclusion-violations proposal)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (or (always-escalate-ops (:op proposal))
                              (high-cost-maintenance-order? proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :route-id   (:route-id request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
