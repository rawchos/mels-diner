(ns mels-diner.kitchen-test
  (:require [clojure.test :refer [deftest is testing]]
            [mels-diner.kitchen :as kitchen]))

(deftest same-order?-test
  (testing "should be the same order if the order ids are the same"
    (is (true? (kitchen/same-order? {:id "order1"} {:id "order1"}))))
  (testing "should not be the same order if the ids are different"
    (is (false? (kitchen/same-order? {:id "order1"} {:id "order2"})))))

(deftest order-exists?-test
  (let [orders [{:id "order2"} {:id "order3"} {:id "order1"}]]
    (testing "should be true if the order exists in the collection"
      (is (true? (kitchen/order-exists? orders {:id "order1"}))))
    (testing "should be false if the order doesn't exist in the collection"
      (is (false? (kitchen/order-exists? orders {:id "order4"}))))))

(deftest remove-order-test
  (let [orders '({:id "order1"} {:id "order2"} {:id "order3"})]
  (testing "should remove the order from a collection of orders if it exists"
    (is (= '({:id "order1"} {:id "order3"})
           (kitchen/remove-order orders {:id "order2"})))
    (is (= '({:id "order1"} {:id "order2"} {:id "order3"})
           (kitchen/remove-order orders {:id "order4"})))
    (is (= '({:id "order2"} {:id "order3"})
           (kitchen/remove-order orders {:id "order1"}))))))

(deftest deliver-order-test
  (let [kitchen-status {:orders-delivered 0
                        :orders-not-delivered 0
                        :shelves {:hot {:orders '({:id "order 1" :temp "hot"}
                                                  {:id "order 2" :temp "hot"})}
                                  :overflow {:orders '({:id "order 3" :temp "hot"}
                                                       {:id "order 4" :temp "hot"})}}}]
    (testing "should increment orders delivered and remove from expected shelf if found"
      (is (= {:orders-delivered 1
              :orders-not-delivered 0
              :shelves {:hot {:orders '({:id "order 2" :temp "hot"})}
                        :overflow {:orders '({:id "order 3" :temp "hot"}
                                             {:id "order 4" :temp "hot"})}}}
             (kitchen/deliver-order kitchen-status {:id "order 1" :temp "hot"}))))
    (testing "should increment orders delivered and remove from overflow shelf if found"
      (is (= {:orders-delivered 1
              :orders-not-delivered 0
              :shelves {:hot {:orders '({:id "order 1" :temp "hot"}
                                        {:id "order 2" :temp "hot"})}
                        :overflow {:orders '({:id "order 3" :temp "hot"})}}}
             (kitchen/deliver-order kitchen-status {:id "order 4" :temp "hot"}))))
    (testing "should increment orders not delivered if not found on expected or overflow shelf"
      (is (= {:orders-delivered 0
              :orders-not-delivered 1
              :shelves {:hot {:orders '({:id "order 1" :temp "hot"}
                                        {:id "order 2" :temp "hot"})}
                        :overflow {:orders '({:id "order 3" :temp "hot"}
                                             {:id "order 4" :temp "hot"})}}}
             (kitchen/deliver-order kitchen-status {:id "order 5" :temp "hot"})))
      (is (= {:orders-delivered 0
              :orders-not-delivered 1
              :shelves {:hot {:orders '({:id "order 1" :temp "hot"}
                                        {:id "order 2" :temp "hot"})}
                        :overflow {:orders '({:id "order 3" :temp "hot"}
                                             {:id "order 4" :temp "hot"})}}}
             (kitchen/deliver-order kitchen-status {:id "order 5" :temp "cold"}))))))

(deftest override-defaults-test
  (testing "should override capacity defaults with values from config"
    (is (= {:shelves {:hot {:capacity 15 :orders '()}
                      :cold {:capacity 20 :orders '()}}}
           (kitchen/override-defaults {:shelves {:hot {:capacity 10 :orders '()}
                                                 :cold {:capacity 10 :orders '()}}}
                                      {:kitchen {:shelves {:hot {:capacity 15}
                                                           :cold {:capacity 20}}}})))))