(ns mels-diner.core
  (:require [mels-diner.config :as cfg]
            [mels-diner.kitchen :as kitchen]
            [taoensso.timbre :as log])
  (:gen-class))

(defn run-simulation
  "Kicks off the orders simulation. It takes in a file of simulation orders or
   defaults to `resources/orders.json` if nothing specified. The kitchen
   is configured based off the `resources/config.edn` file."
  ([] (run-simulation "resources/orders.json"))
  ([orders-file]
   (let [config (-> (cfg/get-config)
                    (assoc :orders-file (or orders-file
                                            "resources/orders.json")))]
     (log/infof "Running with the following config: %s" config)
     (kitchen/prepare-kitchen config)
     (kitchen/simulate-orders config)
     (kitchen/watch-for-completion config))))

(defn -main
  "Entrypoint for application when run from the command line. Defers application
   execution to `run-simulation`."
  [& [orders-file]]
  (run-simulation orders-file))

(comment
  ;; Run from the repl with default orders file:
  (run-simulation)
  
  ;; Run from the repl with specified orders file:
  (run-simulation "resources/small-orders.json")
  )
