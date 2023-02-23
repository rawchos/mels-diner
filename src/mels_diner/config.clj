(ns mels-diner.config
  (:require [mels-diner.util :as util]))

(defn get-config* []
  (util/read-edn "resources/config.edn"))

(def get-config (memoize get-config*))