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

(deftest remove-from-shelf-test
  (let [status {:shelves {:hot {:orders '({:id "hot 1"}
                                          {:id "hot 2"}
                                          {:id "hot 3"})}
                          :cold {:orders '({:id "cold 1"}
                                           {:id "cold 2"})}
                          :overflow {:orders '({:id "cold 3"})}}}]
    (testing "should remove the correct order from the correct shelf"
      (is (= {:shelves {:hot {:orders '({:id "hot 1"}
                                        {:id "hot 3"})}
                        :cold {:orders '({:id "cold 1"}
                                         {:id "cold 2"})}
                        :overflow {:orders '({:id "cold 3"})}}}
             (kitchen/remove-from-shelf status :hot {:id "hot 2"})))
      (is (= {:shelves {:hot {:orders '({:id "hot 1"}
                                        {:id "hot 2"}
                                        {:id "hot 3"})}
                        :cold {:orders '({:id "cold 1"})}
                        :overflow {:orders '({:id "cold 3"})}}}
             (kitchen/remove-from-shelf status :cold {:id "cold 2"})))
      (is (= {:shelves {:hot {:orders '({:id "hot 1"}
                                        {:id "hot 2"}
                                        {:id "hot 3"})}
                        :cold {:orders '({:id "cold 1"}
                                         {:id "cold 2"})}
                        :overflow {:orders '()}}}
             (kitchen/remove-from-shelf status :overflow {:id "cold 3"}))))))

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

(deftest has-capacity?-test
  (testing "should have capacity if the number of orders is less than the capacity"
    (is (true? (kitchen/has-capacity? {:capacity 2 :orders '({:id "order 1"})})))
    (is (true? (kitchen/has-capacity? {:capacity 1 :orders '()}))))
  (testing "should not have capacity if the number of orders is greater than or equal to the capacity"
    (is (false? (kitchen/has-capacity? {:capacity 1 :orders '({:id "order 1"})})))
    (is (false? (kitchen/has-capacity? {:capacity 1 :orders '({:id "order 1"}
                                                              {:id "order 2"})})))
    (is (false? (kitchen/has-capacity? {:capacity 0 :orders '()})))))

(deftest add-to-shelf-test
  (let [status {:shelves {:hot {:capacity 10 :orders '()}
                          :cold {:capacity 10 :orders '({:id "cold 1"})}
                          :overflow {:capacity 15 :orders '({:id "frozen 1"}
                                                            {:id "hot 1"})}}}]
    (testing "should add the order to the specified shelf"
      (is (= {:shelves {:hot {:capacity 10 :orders '({:id "hot 2"})}
                        :cold {:capacity 10 :orders '({:id "cold 1"})}
                        :overflow {:capacity 15 :orders '({:id "frozen 1"}
                                                          {:id "hot 1"})}}}
             (kitchen/add-to-shelf status :hot {:id "hot 2"})))
      (is (= {:shelves {:hot {:capacity 10 :orders '()}
                        :cold {:capacity 10 :orders '({:id "cold 2"}
                                                      {:id "cold 1"})}
                        :overflow {:capacity 15 :orders '({:id "frozen 1"}
                                                          {:id "hot 1"})}}}
             (kitchen/add-to-shelf status :cold {:id "cold 2"})))
      (is (= {:shelves {:hot {:capacity 10 :orders '()}
                        :cold {:capacity 10 :orders '({:id "cold 1"})}
                        :overflow {:capacity 15 :orders '({:id "frozen 2"}
                                                          {:id "frozen 1"}
                                                          {:id "hot 1"})}}}
             (kitchen/add-to-shelf status :overflow {:id "frozen 2"}))))))

(deftest find-order-to-shuffle-test
  (testing "should find the first order with shelf capacity starting from the end of the overflow list"
    (is (= {:id "this one" :temp "hot"}
           (kitchen/find-order-to-shuffle {:shelves {:hot {:capacity 2
                                                           :orders '({:id "hot 1"})}
                                                     :cold {:capacity 0
                                                            :orders '()}
                                                     :frozen {:capacity 1
                                                              :orders '()}
                                                     :overflow {:capacity 5
                                                                :orders '({:id "is available" :temp "frozen"}
                                                                          {:id "also available" :temp "hot"}
                                                                          {:id "not available" :temp "cold"}
                                                                          {:id "this one" :temp "hot"}
                                                                          {:id "not me" :temp "cold"})}}}))))
  (testing "should be nil if no orders are available to move over"
    (is (nil? (kitchen/find-order-to-shuffle {:shelves {:hot {:capacity 1
                                                              :orders '({:id "hot 1"})}
                                                        :cold {:capacity 0
                                                               :orders '()}
                                                        :frozen {:capacity 0
                                                                 :orders '()}
                                                        :overflow {:capacity 5
                                                                   :orders '({:id "not available" :temp "frozen"}
                                                                             {:id "also not available" :temp "hot"}
                                                                             {:id "nope" :temp "cold"}
                                                                             {:id "not this one" :temp "hot"}
                                                                             {:id "not me" :temp "cold"})}}})))))

(deftest shuffle-or-drop-overflow-test
  (testing "should move an order from overflow if available"
    (is (= {:shelves {:hot {:capacity 1
                            :orders '({:id "hot 1" :temp "hot"})}
                      :cold {:capacity 0
                             :orders '()}
                      :overflow {:capacity 5
                                 :orders '({:id "hot 2" :temp "hot"}
                                           {:id "cold 1" :temp "cold"})}}}
           (kitchen/shuffle-or-drop-overflow {:shelves {:hot {:capacity 1
                                                              :orders '()}
                                                        :cold {:capacity 0
                                                               :orders '()}
                                                        :overflow {:capacity 5
                                                                   :orders '({:id "hot 2" :temp "hot"}
                                                                             {:id "hot 1" :temp "hot"}
                                                                             {:id "cold 1" :temp "cold"})}}}))))
  (testing "should drop the last overflow order if none are movable"
    (is (= {:shelves {:hot {:capacity 1
                            :orders '({:id "hot 3" :temp "hot"})}
                      :cold {:capacity 0
                             :orders '()}
                      :overflow {:capacity 5
                                 :orders '({:id "hot 2" :temp "hot"}
                                           {:id "hot 1" :temp "hot"})}}}
           (kitchen/shuffle-or-drop-overflow {:shelves {:hot {:capacity 1
                                                              :orders '({:id "hot 3" :temp "hot"})}
                                                        :cold {:capacity 0
                                                               :orders '()}
                                                        :overflow {:capacity 5
                                                                   :orders '({:id "hot 2" :temp "hot"}
                                                                             {:id "hot 1" :temp "hot"}
                                                                             {:id "cold 1" :temp "cold"})}}})))))

(deftest place-order-test
  (testing "should place the order on the expected shelf if room available"
    (is (= {:orders-placed 10
            :shelves {:hot {:capacity 2
                            :orders '({:id "hot 1" :temp "hot"}
                                      {:id "hot 2" :temp "hot"})}
                      :overflow {:capacity 2
                                 :orders '()}}}
           (kitchen/place-order {:orders-placed 9
                                 :shelves {:hot {:capacity 2
                                                 :orders '({:id "hot 2" :temp "hot"})}
                                           :overflow {:capacity 2
                                                      :orders '()}}}
                                {:id "hot 1" :temp "hot"}))))
  (testing "should place the order on overflow if space available"
    (is (= {:orders-placed 10
            :shelves {:hot {:capacity 2
                            :orders '({:id "hot 1" :temp "hot"}
                                      {:id "hot 2" :temp "hot"})}
                      :overflow {:capacity 2
                                 :orders '({:id "hot 3" :temp "hot"})}}}
           (kitchen/place-order {:orders-placed 9
                                 :shelves {:hot {:capacity 2
                                                 :orders '({:id "hot 1" :temp "hot"}
                                                           {:id "hot 2" :temp "hot"})}
                                           :overflow {:capacity 2
                                                      :orders '()}}}
                                {:id "hot 3" :temp "hot"}))))
  (testing "should shuffle an order off overflow if possible"
    (is (= {:orders-placed 10
            :shelves {:hot {:capacity 2
                            :orders '({:id "hot 1" :temp "hot"}
                                      {:id "hot 2" :temp "hot"})}
                      :cold {:capacity 1
                             :orders '({:id "cold 2" :temp "cold"})}
                      :overflow {:capacity 2
                                 :orders '({:id "hot 3" :temp "hot"}
                                           {:id "cold 1" :temp "cold"})}}}
           (kitchen/place-order {:orders-placed 9
                                 :shelves {:hot {:capacity 2
                                                 :orders '({:id "hot 1" :temp "hot"}
                                                           {:id "hot 2" :temp "hot"})}
                                           :cold {:capacity 1
                                                  :orders '()}
                                           :overflow {:capacity 2
                                                      :orders '({:id "cold 1" :temp "cold"}
                                                                {:id "cold 2" :temp "cold"})}}}
                                {:id "hot 3" :temp "hot"}))))
  (testing "should drop an order off overflow if no shuffling possible"
    (is (= {:orders-placed 10
            :shelves {:hot {:capacity 2
                            :orders '({:id "hot 1" :temp "hot"}
                                      {:id "hot 2" :temp "hot"})}
                      :cold {:capacity 0
                             :orders '()}
                      :overflow {:capacity 2
                                 :orders '({:id "hot 3" :temp "hot"}
                                           {:id "cold 1" :temp "cold"})}}}
           (kitchen/place-order {:orders-placed 9
                                 :shelves {:hot {:capacity 2
                                                 :orders '({:id "hot 1" :temp "hot"}
                                                           {:id "hot 2" :temp "hot"})}
                                           :cold {:capacity 0
                                                  :orders '()}
                                           :overflow {:capacity 2
                                                      :orders '({:id "cold 1" :temp "cold"}
                                                                {:id "cold 2" :temp "cold"})}}}
                                {:id "hot 3" :temp "hot"})))))