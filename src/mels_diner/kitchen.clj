(ns mels-diner.kitchen
  ;; TODO: Maybe just alias core.async because this is getting to 
  ;;       be a large refer list
  (:require [clojure.core.async :refer [chan close! go go-loop timeout <! <!! >!]]
            [mels-diner.util :as util]
            [taoensso.timbre :as log]))

(def orders-intake (chan))

(def kitchen-status
  "Sets the initial default status. Capacity for each shelf can be overridden
   from the config during kitchen preperation."
  (agent {:orders-placed 0
          :orders-delivered 0
          :orders-not-delivered 0
          :shelves {:hot      {:capacity 10
                               :orders '()}
                    :cold     {:capacity 10
                               :orders '()}
                    :frozen   {:capacity 10
                               :orders '()}
                    :overflow {:capacity 15
                               :orders '()}}}))

(defn watch-status-changes
  "Watches the kitchen status and prints the current status every time it changes."
  []
  (let [watch-fn (fn [k _ref _old-status new-status]
                   (log/infof "[%s] Kitchen status updated: %s" k new-status))]
    (add-watch kitchen-status :change-notification watch-fn)))

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

(defn same-order?
  "Checks to see if 2 orders are equivalent based on their order id."
  [order1 order2]
  (= (:id order1) (:id order2)))

(defn order-exists?
  "Checks to see if a specific order exists on a shelf's collection of orders."
  [orders order]
  (-> (filter (partial same-order? order) orders)
      count
      pos?))

(defn remove-order
  "Removes a specific order from a list of orders."
  [orders order]
  (remove (partial same-order? order) orders))

(defn deliver-order
  "Looks for an order on it's expected shelf in the kitchen or on the overflow
   shelf and removes it for delivery. Doesn't remove anything if the order isn't
   found. Determines order equality by order id."
  [kitchen-status {:keys [temp] :as order}]
  (let [expected-shelf (keyword temp)]
    (cond
      (order-exists? (get-in kitchen-status [:shelves expected-shelf :orders]) order)
      (-> kitchen-status
          (update :orders-delivered inc)
          (update-in [:shelves expected-shelf :orders] remove-order order))
      
      (order-exists? (get-in kitchen-status [:shelves :overflow :orders]) order)
      (-> kitchen-status
          (update :orders-delivered inc)
          (update-in [:shelves :overflow :orders] remove-order order))
      
      :else
      (update kitchen-status :orders-not-delivered inc))))

(defn dispatch-courier
  "Dispatches a courier to pickup a specific order from the kitchen after the
   specified delay."
  [order delay-seconds]
  (go
    (<! (timeout (seconds->millis delay-seconds)))
    (log/infof "Picking up order [%s]" (:id order))
    (send kitchen-status deliver-order order)))

(defn receive-orders
  [{{:keys [min-courier-delay max-courier-delay]} :kitchen}]
  (go-loop []
    (when-some [{:keys [id temp] :as order} (<! orders-intake)]
      (log/infof "Received this order: %s" order)
      (let [delayed-pickup (->> (inc max-courier-delay)
                                (range min-courier-delay)
                                rand-nth)]
        (log/infof "Dispatching courier to pickup order id [%s] from the %s shelf in %d seconds"
                   id temp delayed-pickup)
        (dispatch-courier order delayed-pickup))
      (recur))))

(defn override-defaults [kitchen-defaults {{:keys [shelves]} :kitchen}]
  (reduce-kv (fn [m shelf {:keys [capacity]}]
               (assoc-in m [:shelves shelf :capacity] capacity))
             kitchen-defaults
             shelves))

;; TODO: Add kitchen-status watcher for printing out changes.
(defn prepare-kitchen
  "Sets up the kitchen and prepares it to receive orders."
  [config]
  (send kitchen-status override-defaults config)
  (watch-status-changes)
  (receive-orders config))


(comment
  (receive-orders {:kitchen {:min-courier-delay 2
                             :max-courier-delay 6}})
  (simulate-orders {:orders-file "resources/small-orders.json"
                    :ingestion-count 2
                    :ingestion-rate 1})
  
  (conj '(:one :two :three) :four)
  (drop-last '(:one :two :three))
  
  (rand-nth (range 2 (inc 6)))
  
  )