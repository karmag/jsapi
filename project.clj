(defproject karmag.jsapi "0.1.0-SNAPSHOT"
  :description "Low-level hi-perf(?) clojure prototype"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 ;; config
                 [karmag.datum "0.2.0-SNAPSHOT"] ;; configuration
                 ;; http
                 [org.apache.httpcomponents/httpcore "4.4.5"]
                 ;; json
                 ;; - databind is used for its token buffer
                 [com.fasterxml.jackson.core/jackson-core "2.8.5"]
                 [com.fasterxml.jackson.core/jackson-databind "2.8.5"]
                 ;; database
                 [com.taoensso/carmine "2.15.0"]
                 [com.datastax.cassandra/cassandra-driver-core "3.1.4"]]
  :global-vars {*warn-on-reflection* true}
  :main karmag.jsapi.core)
