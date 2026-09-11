(ns ferryops.store
  "SSoT for the ISIC-5021 'Inland passenger water transport' (river
  ferries, lake excursion boats, canal passenger boats) dispatch/
  scheduling operations-COORDINATION actor, behind a `Store` protocol so
  the backend is a swap, not a rewrite -- the same seam every
  `cloud-itonami-isic-*` actor in this fleet uses.

  This actor coordinates the back-office SCHEDULING/DISPATCH LOGISTICS of
  an inland passenger-water-transport operator: voyage/ridership/
  incident-report data logging, ferry-crossing/timetable scheduling,
  vessel-maintenance procurement coordination with a registered
  maintenance contractor, and safety-concern flagging (vessel defects,
  overloading risk, captain-fitness concerns). It NEVER directly operates
  a vessel, NEVER finalizes a vessel-seaworthiness clearance, NEVER
  overrides a certified passenger-capacity limit, and NEVER makes a
  captain-fitness determination itself -- see `ferryops.governor`'s
  `scope-exclusion-violations`, a HARD, permanent, un-overridable block.

  `MemStore` -- atom of EDN. The deterministic default for dev/tests/demo
  (no deps). A `routes` directory keyed by `:route-id` STRING (a
  scheduled ferry crossing/route, itself naming the assigned vessel and
  the operator's license status) and a `contractors` directory keyed by
  `:contractor-id` STRING (a vessel-maintenance contractor, never
  keywords -- consistent keying from the start, avoiding the silent-miss
  bug that has plagued earlier sibling actors).

  A registered/verified route record (vessel identity + operator-license
  status, independently confirmed) must exist before ANY proposal
  targeting that route may ever commit or escalate --
  `ferryops.governor`'s `route-unverified-violations` re-derives this
  from the route's own `:registered?`/`:verified?` fields, never from
  proposal self-report. A `:coordinate-maintenance-order` proposal
  additionally names a registered maintenance contractor via its own
  `:contractor-id`; the SAME 'ground truth, not self-report' discipline
  applies via `contractor-unverified-violations` -- the flagship
  genuinely new check this vertical adds (a vessel-maintenance-
  counterparty-verification gate no sibling 50xx actor has).

  The ledger stays append-only: which route a proposal targeted, which
  operation, on what basis, committed/held/escalated and approved by whom
  is always a query over an immutable log.")

(defprotocol Store
  (route-record [s route-id] "Registered ferry-route/crossing record, or nil.
    Route map: {:route-id .. :name .. :vessel-id .. :registered? bool :verified? bool}.
    `:registered?`/`:verified?` jointly stand in for 'the vessel identity
    and operator license behind this route have been independently
    confirmed' -- this actor never issues or re-derives a seaworthiness
    clearance itself, it only refuses to coordinate against a route whose
    vessel/operator-license record has not been independently verified
    elsewhere.")
  (all-route-records [s])
  (contractor-record [s contractor-id] "Registered vessel-maintenance contractor record, or nil.
    Contractor map: {:contractor-id .. :name .. :registered? bool :verified? bool}.")
  (all-contractor-records [s])
  (ledger [s] "the append-only immutable decision-fact log")
  (coordination-log [s] "the append-only committed coordination-proposal history")
  (commit-record! [s record] "apply a committed proposal's record to the SSoT")
  (append-ledger! [s fact] "append one immutable decision fact")
  (with-route-records [s routes] "replace/seed the route directory (map route-id->route)")
  (with-contractor-records [s contractors] "replace/seed the contractor directory (map contractor-id->contractor)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained route/contractor directory covering both the
  happy path and the governor's own hard checks, so the actor + tests run
  offline."
  []
  {:routes
   {"route-1" {:route-id "route-1" :name "Riverside Crossing (Route 1)"
               :vessel-id "vessel-1" :registered? true :verified? true}
    "route-2" {:route-id "route-2" :name "Lake Shore Excursion Loop"
               :vessel-id "vessel-2" :registered? true :verified? true}
    "route-3" {:route-id "route-3" :name "Canal Passenger Route (in intake)"
               :vessel-id "vessel-3" :registered? true :verified? false}}
   :contractors
   {"contractor-1" {:contractor-id "contractor-1" :name "Riverside Marine Maintenance Co."
                     :registered? true :verified? true}
    "contractor-2" {:contractor-id "contractor-2" :name "Unverified Dockside Repair Ltd."
                     :registered? true :verified? false}}})

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (route-record [_ route-id] (get-in @a [:routes route-id]))
  (all-route-records [_] (sort-by :route-id (vals (:routes @a))))
  (contractor-record [_ contractor-id] (get-in @a [:contractors contractor-id]))
  (all-contractor-records [_] (sort-by :contractor-id (vals (:contractors @a))))
  (ledger [_] (:ledger @a))
  (coordination-log [_] (:coordination-log @a))
  (commit-record! [_ record]
    (swap! a update :coordination-log conj record)
    record)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-route-records [s routes] (when (seq routes) (swap! a assoc :routes routes)) s)
  (with-contractor-records [s contractors] (when (seq contractors) (swap! a assoc :contractors contractors)) s))

(defn seed-db
  "A MemStore seeded with the demo route/contractor directory. The
  deterministic default."
  []
  (->MemStore (atom (assoc (demo-data) :ledger [] :coordination-log []))))

(defn mem-store
  "A MemStore seeded with explicit `routes`/`contractors` maps (route-id/
  contractor-id string -> record map) -- the primary test/dev entry
  point. Either may be empty (an unregistered-everywhere route)."
  ([routes] (mem-store routes {}))
  ([routes contractors]
   (->MemStore (atom {:routes (or routes {}) :contractors (or contractors {})
                       :ledger [] :coordination-log []}))))
