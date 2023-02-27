(ns mels-diner.kitchen
  (:require [clojure.core.async :refer [chan close! go go-loop timeout <! <!! >!]]
            [clojure.pprint :refer [pprint]]
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
                   (log/infof "[%s] Kitchen status updated: \n%s"
                              k (with-out-str (pprint new-status))))]
    (add-watch kitchen-status :change-notification watch-fn)))

(defn seconds->millis [seconds]
  (* seconds 1000))

(defn simulate-orders
  "Simulates receiving orders. Order intake rate and file containing simulated
   orders are passed in the config."
  [{:keys [ingestion-count ingestion-rate orders-file]}]
  (log/infof "Placing orders from [%s] at an ingestion rate of %d orders every %d seconds"
             orders-file ingestion-count ingestion-rate)
  (loop [all-orders (->> (util/load-orders orders-file)
                         (partition-all ingestion-count))]
    (when-let [orders (first all-orders)]
      (doseq [order orders]
        (go (>! orders-intake order)))
      (<!! (timeout (seconds->millis ingestion-rate)))
      (recur (rest all-orders))))
  (close! orders-intake))

(defn add-change-message
  "Adds a change message to the kitchen status with an optional flag to
   overwrite what's currently there or just add to it."
  ([kitchen-status message]
   (add-change-message kitchen-status message false))
  ([kitchen-status message overwrite?]
   (if overwrite?
     (assoc kitchen-status :changes [message])
     (update kitchen-status :changes conj message))))

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
  [{:keys [shelves]}]
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
  (if-let [{:keys [id temp] :as movable-order} (find-order-to-shuffle kitchen-status)]
    (-> kitchen-status
        (add-to-shelf (keyword temp) movable-order)
        (remove-from-shelf :overflow movable-order)
        (add-change-message (format "Moved order [%s] to %s shelf from overflow" id temp)))
    (-> (update-in kitchen-status [:shelves :overflow :orders] drop-last)
        (add-change-message (format "Dropped last order from overflow")))))

(defn place-order
  "Places an order on its expected shelf according to temperature. If there isn't
   room on its expected shelf, it then defaults to placing on the overflow shelf.
   If there isn't room on the overflow shelf, it will attempt to move an order
   from overflow to a shelf with capacity. If unable to move an order, drops
   the oldest order from the overflow shelf."
  [kitchen-status {:keys [id temp] :as order}]
  (let [expected-shelf (keyword temp)
        mutated-status (-> (update kitchen-status :orders-placed inc)
                           (add-change-message "Incremented orders placed" true))]
    (cond 
      (has-capacity? (get-in mutated-status [:shelves expected-shelf]))
      (-> (add-to-shelf mutated-status expected-shelf order)
          (add-change-message (format "Added order [%s] to %s shelf" id temp)))
      
      (has-capacity? (get-in mutated-status [:shelves :overflow]))
      (-> (add-to-shelf mutated-status :overflow order)
          (add-change-message (format "Added order [%s] to overflow shelf" id)))
      
      :else
      (-> (shuffle-or-drop-overflow mutated-status)
          (add-to-shelf :overflow order)
          (add-change-message (format "Added order [%s] to overflow shelf" id))))))

(defn deliver-order
  "Looks for an order on its expected shelf in the kitchen or on the overflow
   shelf and removes it for delivery. Doesn't remove anything if the order isn't
   found. Determines order equality by order id."
  [kitchen-status {:keys [id temp] :as order}]
  (let [expected-shelf (keyword temp)]
    (cond
      (order-exists? (get-in kitchen-status [:shelves expected-shelf :orders]) order)
      (-> kitchen-status
          (update :orders-delivered inc)
          (remove-from-shelf expected-shelf order)
          (add-change-message (format (str "Delivered order [%s] from %s shelf "
                                           "and incremented orders delivered")
                                      id temp) true))
      
      (order-exists? (get-in kitchen-status [:shelves :overflow :orders]) order)
      (-> kitchen-status
          (update :orders-delivered inc)
          (remove-from-shelf :overflow order)
          (add-change-message (format (str "Delivered order [%s] from overflow "
                                           "shelf and incremented orders delivered")
                                      id) true))
      
      :else
      (-> (update kitchen-status :orders-not-delivered inc)
          (add-change-message (format "Incremented orders not delivered for order [%s]"
                                      id) true)))))

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
      (log/infof "Received order [%s]" order)
      (send kitchen-status place-order order)
      (let [delayed-pickup (->> (inc max-courier-delay)
                                (range min-courier-delay)
                                rand-nth)]
        (log/infof "Dispatching courier to pickup order [%s] from the %s shelf in %d seconds"
                   id temp delayed-pickup)
        (dispatch-courier order delayed-pickup))
      (recur))))

(defn override-defaults [kitchen-defaults {{:keys [shelves]} :kitchen}]
  (-> (reduce-kv (fn [m shelf {:keys [capacity]}]
                   (assoc-in m [:shelves shelf :capacity] capacity))
                 kitchen-defaults
                 shelves)
      (add-change-message "Updated kitchen config" true)))

(defn prepare-kitchen
  "Sets up the kitchen and prepares it to receive orders."
  [config]
  (send kitchen-status override-defaults config)
  (watch-status-changes)
  (receive-orders config))

(defn orders-complete?
  "Compares the number of orders placed against the sum of orders delivered and
   not delivered."
  [{:keys [orders-placed orders-delivered orders-not-delivered]}]
  (= orders-placed (+ orders-delivered orders-not-delivered)))

(defn watch-for-completion
  "Watch for all orders to either be delivered or dropped then shutdown the
   agents threads. In case there's a discrepancy between the order placed and
   the orders deliver/not delivered, loop 3 times using the max courier delay.
   This should give enough time for everything to complete."
  [{{:keys [max-courier-delay]} :kitchen}]
  (loop [times (range 3)]
    (if-let [_ (first times)]
      (if (orders-complete? @kitchen-status)
        (do
          (log/info "Order processing complete. Shutting down.")
          (shutdown-agents))
        (do
          (<!! (timeout (seconds->millis max-courier-delay)))
          (recur (rest times))))
      (do
        (log/info "Discrepancy in orders placed vs orders (not) delivered. Shutting down.")
        (shutdown-agents)))))
