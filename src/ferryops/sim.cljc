(ns ferryops.sim
  "Demo driver -- `clojure -M:run`. Walks a clean service-record logging
  request through intake -> advise -> govern -> decide -> approval ->
  commit at phase 1 (assisted-logging, always approval), then re-runs the
  same op at phase 3 (supervised-auto, clean + high confidence ->
  auto-commit), then a crossing-schedule request and a low-cost
  maintenance-order coordination naming a verified contractor (both
  auto-commit clean at phase 3), then a high-cost maintenance-order
  (ALWAYS escalates regardless of phase), then a safety-concern flag
  (ALWAYS escalates, at any phase -- approve, then commit), then
  HARD-hold scenarios: an unregistered route, a route registered but not
  yet verified, a maintenance-order naming an unverified contractor, a
  proposal whose own `:effect` is not `:propose`, and a proposal that has
  drifted into the permanently-excluded seaworthiness/capacity/captain-
  fitness-finalization scope."
  (:require [langgraph.graph :as g]
            [ferryops.advisor :as advisor]
            [ferryops.store :as store]
            [ferryops.operation :as op]))

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "dispatch-coordinator-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        coordinator-phase-1 {:actor-id "coord-1" :actor-role :ferry-dispatch-coordinator :phase 1}
        coordinator-phase-3 {:actor-id "coord-1" :actor-role :ferry-dispatch-coordinator :phase 3}
        actor (op/build db)]

    (println "== log-service-record route-1 (phase 1, escalates -- human approves) ==")
    (let [r (exec-op actor "t1" {:op :log-service-record :route-id "route-1"
                                  :patch {:crossings-completed 6 :passengers 214 :incident-reports 0}} coordinator-phase-1)]
      (println r)
      (println "-- human dispatch coordinator approves --")
      (println (approve! actor "t1")))

    (println "\n== log-service-record route-1 (phase 3, clean -- auto-commits) ==")
    (println (exec-op actor "t2" {:op :log-service-record :route-id "route-1"
                                  :patch {:crossings-completed 4 :passengers 150 :incident-reports 0}} coordinator-phase-3))

    (println "\n== schedule-crossing-operation route-1 (phase 3, clean -- auto-commits) ==")
    (println (exec-op actor "t3" {:op :schedule-crossing-operation :route-id "route-1"
                                  :patch {:crossing "afternoon-loop" :date "2026-07-20" :window "13:00-17:00"}} coordinator-phase-3))

    (println "\n== coordinate-maintenance-order route-1, low cost, verified contractor (phase 3, clean -- auto-commits) ==")
    (println (exec-op actor "t4" {:op :coordinate-maintenance-order :route-id "route-1"
                                  :patch {:item "engine inspection service" :estimated-cost 650.0
                                          :contractor-id "contractor-1"}} coordinator-phase-3))

    (println "\n== coordinate-maintenance-order route-1, HIGH cost (ALWAYS escalates, even at phase 3) ==")
    (let [r (exec-op actor "t5" {:op :coordinate-maintenance-order :route-id "route-1"
                                 :patch {:item "hull dry-dock overhaul" :estimated-cost 8600.0
                                         :contractor-id "contractor-1"}} coordinator-phase-3)]
      (println r)
      (println "-- human dispatch coordinator reviews & approves --")
      (println (approve! actor "t5")))

    (println "\n== flag-safety-concern route-1 (ALWAYS escalates, even at phase 3) ==")
    (let [r (exec-op actor "t6" {:op :flag-safety-concern :route-id "route-1"
                                 :patch {:concern "observed unusual list to port during boarding, peak-hour ridership approaching posted capacity" :confidence 0.92}} coordinator-phase-3)]
      (println r)
      (println "-- human dispatch coordinator reviews & approves --")
      (println (approve! actor "t6")))

    (println "\n== log-service-record route-99 (unregistered route -> HARD hold) ==")
    (println (exec-op actor "t7" {:op :log-service-record :route-id "route-99"
                                  :patch {:crossings-completed 0}} coordinator-phase-3))

    (println "\n== log-service-record route-3 (registered but unverified -> HARD hold) ==")
    (println (exec-op actor "t8" {:op :log-service-record :route-id "route-3"
                                  :patch {:crossings-completed 1}} coordinator-phase-3))

    (println "\n== coordinate-maintenance-order route-1, contractor-2 unverified (-> HARD hold) ==")
    (println (exec-op actor "t9" {:op :coordinate-maintenance-order :route-id "route-1"
                                  :patch {:item "dock-side repair" :estimated-cost 300.0
                                          :contractor-id "contractor-2"}} coordinator-phase-3))

    (println "\n== schedule-crossing-operation route-1, advisor attempts direct actuation (:effect :commit) -> HARD hold ==")
    (let [actor-direct (op/build db {:advisor (reify advisor/Advisor
                                                (-advise [_ _ req]
                                                  (assoc (advisor/infer nil req) :effect :commit)))})]
      (println (exec-op actor-direct "t10" {:op :schedule-crossing-operation :route-id "route-1"
                                           :patch {:crossing "weekday-loop" :date "2026-07-22"}} coordinator-phase-3)))

    (println "\n== log-service-record route-1, advisor drifts into seaworthiness/capacity/captain-fitness-finalization scope -> HARD hold, permanent ==")
    (println (exec-op actor "t11" {:op :log-service-record :route-id "route-1"
                                   :out-of-scope? true
                                   :patch {}} coordinator-phase-3))

    (println "\n== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "\n== committed coordination log ==")
    (doseq [r (store/coordination-log db)] (println r))))
