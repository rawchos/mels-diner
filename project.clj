(defproject mels-diner "0.1.0-SNAPSHOT"
  :description "Order simulation for a kitchen."
  :url "https://github.com/rawchos/mels-diner"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.3"]
                 [org.clojure/core.async "1.6.673"]
                 [cheshire/cheshire "5.11.0"]
                 [com.taoensso/timbre "5.2.1"]]
  :main ^:skip-aot mels-diner.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})
