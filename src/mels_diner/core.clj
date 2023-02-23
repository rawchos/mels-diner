(ns mels-diner.core
  (:require [mels-diner.config :as cfg]
            [mels-diner.kitchen :as kitchen]
            [taoensso.timbre :as log])
  (:gen-class))

;; TODO: Currently defaulting to resources/small-orders.json. Switch to
;;       resources/orders.json before completing. Also, expecting to use
;;       resources/config.edn for kitchen configuration so implement that.
;;
;;       Also, be aware of when program execution stops and if we still have
;;       go processes in flight. Might need a check in here that's waiting for
;;       kitchen shutdown notification.
(defn -main
  "Kicks off the orders simulation. It takes in a file of simulation orders or
   defaults to `resources/small-orders.json` if nothing specified. The kitchen
   is configured based off the `resources/config.edn` file."
  [& [orders-file]]
  (let [config (-> (cfg/get-config)
                   (assoc :orders-file (or orders-file
                                           "resources/small-orders.json")))]
    (log/infof "Running with the following config: %s" config)
    (kitchen/receive-orders)
    (kitchen/simulate-orders config)))
