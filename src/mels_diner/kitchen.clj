(ns mels-diner.kitchen
  ;; TODO: Maybe just alias core.async because this is getting to 
  ;;       be a large refer list
  (:require [clojure.core.async :refer [chan close! go go-loop timeout <! <!! >!]]
            [mels-diner.util :as util]
            [taoensso.timbre :as log]))

(def orders-intake (chan))

(defn seconds->millis [seconds]
  (* seconds 1000))

;; TODO: I'm not sure I like the nested doseq. Possibly revisit but good enough
;;       to continue forward for now.
(defn simulate-orders
  "Simulates receiving orders. Order intake rate and file containing simulated
   orders are passed in the config."
  [{:keys [ingestion-count ingestion-rate orders-file]}]
  (log/infof "Placing orders from [%s] at an ingestion rate of %d orders every %d seconds"
             orders-file ingestion-count ingestion-rate)
  (doseq [orders (->> (util/load-orders orders-file)
                      (partition-all ingestion-count))]
    (doseq [order orders]
      (go (>! orders-intake order)))
    (<!! (timeout (seconds->millis ingestion-rate))))
  (close! orders-intake))

(defn receive-orders
  []
  (go-loop []
    (when-some [order (<! orders-intake)]
      (log/infof "Received this order: %s" order)
      (recur))))

(comment
  (receive-orders)
  (simulate-orders {:orders-file "resources/small-orders.json"
                    :ingestion-count 2
                    :ingestion-rate 1})

  )