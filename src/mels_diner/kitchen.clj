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

(defn has-capacity?
  "Determines if a shelf has capacity to add another order."
  [{:keys [capacity orders]}]
  (< (count orders) capacity))

(defn add-to-shelf
  "Adds an order to the specified shelf."
  [kitchen-status shelf order]
  (update-in kitchen-status [:shelves shelf :orders] conj order))

(defn remove-from-shelf
  "Removes an order from the specified shelf."
  [kitchen-status shelf order]
  (update-in kitchen-status [:shelves shelf :orders] remove-order order))

(defn find-order-to-shuffle
  "Finds an order from the overflow shelf that can be moved to its actual shelf
   according to the temp. Returns `nil` if no orders found."
  [{{:keys [shelves]} :kitchen}]
  (loop [orders (-> shelves :overflow :orders reverse)
         checked-shelves #{}]
    (if-let [{:keys [temp] :as order} (first orders)]
      (if (and (not (contains? checked-shelves temp))
               (has-capacity? (get shelves (keyword temp))))
        order
        (recur (rest orders) (conj checked-shelves temp)))
      nil)))

(defn shuffle-or-drop-overflow
  "Attempts to move an order from the overflow shelf to its expected shelf by temp.
   If no orders are found in the overflow shelf that can be moved to their expected
   shelf, drops the last order from the overflow shelf. The dropped order becomes
   waste and will not be delivered."
  [kitchen-status]
  (if-let [{:keys [temp] :as movable-order} (find-order-to-shuffle kitchen-status)]
    (-> kitchen-status
        (add-to-shelf (keyword temp) movable-order)
        (remove-from-shelf :overflow movable-order))
    (update-in kitchen-status [:shelves :overflow :orders] drop-last)))

(defn place-order
  "Places an order on its expected shelf according to temperature. If there isn't
   room on its expected shelf, it then defaults to placing on the overflow shelf.
   If there isn't room on the overflow shelf, it will attempt to move an order
   from overflow to a shelf with capacity. If unable to move an order, drops
   the oldest order from the overflow shelf."
  [kitchen-status {:keys [temp] :as order}]
  (let [expected-shelf     (keyword temp)
        incremented-status (update kitchen-status :orders-placed inc)]
    (cond 
      (has-capacity? (get-in incremented-status [:shelves expected-shelf]))
      (add-to-shelf incremented-status expected-shelf order)
      
      (has-capacity? (get-in incremented-status [:shelves :overflow]))
      (add-to-shelf incremented-status :overflow order)
      
      :else
      (-> (shuffle-or-drop-overflow incremented-status)
          (add-to-shelf :overflow order)))))

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
          (remove-from-shelf expected-shelf order))
      
      (order-exists? (get-in kitchen-status [:shelves :overflow :orders]) order)
      (-> kitchen-status
          (update :orders-delivered inc)
          (remove-from-shelf :overflow order))
      
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
      (send kitchen-status place-order order)
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
  
  (let [some-status {:shelves {:overflow {:capacity 10
                                          :orders '({:id "order 1"}
                                                    {:id "order 2"})}}}]
    (update-in some-status [:shelves :overflow :orders] drop-last))
  
  )