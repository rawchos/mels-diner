(ns mels-diner.util
  (:require [cheshire.core :refer [parse-string]]
            [clojure.edn :as edn]
            [clojure.java.io :as io])
  (:import (java.io PushbackReader)))

(defn read-edn [filename]
  (edn/read (PushbackReader. (io/reader filename))))

(defn load-orders
  "Loads simulation orders from a file."
  [filename]
  (-> (slurp filename)
      (parse-string true)))