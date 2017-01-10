(ns karmag.jsapi.core
  (:require [clojure.java.io :as io]
            [karmag.jsapi.database :as db]
            [karmag.jsapi.definition :as d]
            [karmag.jsapi.json :as js]
            [karmag.jsapi.protocol :refer :all]
            [karmag.jsapi.resource :as r]
            [taoensso.carmine :as car :refer [wcar]]))

(def definition
  (-> "test-definition.clj" io/resource d/read-definition))

(def create-resource
  (r/make definition))

(def parse-json
  (js/make definition create-resource))

(def database
  (db/make "localhost" 6379 create-resource))

(def js-data "{
  \"data\": {
    \"type\": \"person\",
    \"id\": \"hello123\",
    \"attributes\": {
      \"name\": \"karl\",
      \"prio\": 5,
      \"valid-for\": {
        \"start\": \"tomorrow\"
      }
    },
    \"meta\": {
      \"version\": 1230
    }
  }
}")

(defn -main []
  (println "Main running ...")
  (try
    (let [gen-id #(str (rand-int Integer/MAX_VALUE))

          parse-resource (fn []
                           (let [res (:data (parse-json js-data))]
                             (set-attr res ["id"] (gen-id))
                             res))

          gen-resource (let [res (parse-resource)]
                         #(do (set-attr res ["id"] (gen-id))
                              res))

          write-resource-fast (fn [] (save-resource database (gen-resource)))
          write-resource (fn [res] (save-resource database res))
          write-data (fn [] (wcar {:host "localhost" :port 6379}
                                  (car/set (gen-id) "data goes here")))

          fns [["small write" write-data]
               ["resource write" write-resource-fast]
               ["resource parse" parse-resource]
               ["parse + write" (comp write-resource parse-resource)]]]

      (doseq [[f-name f] fns]
        (doseq [thread-count [2 4 8 10 12 16 32]]
          (let [counter (atom 0)
                running (atom true)
                threads (->> #(Thread. (fn [] (while @running
                                                (f)
                                                (swap! counter inc))))
                             repeatedly
                             (take thread-count)
                             doall)]
            (doseq [t threads] (.start ^Thread t))
            (let [start-count @counter
                  start-time (System/currentTimeMillis)]
              (Thread/sleep 5000)
              (let [end-count @counter
                    end-time (System/currentTimeMillis)]
                (reset! running false)
                (doseq [t threads] (.join ^Thread t))
                (let [processed (- end-count start-count)
                      seconds (float (/ (- end-time start-time)
                                        1000))]
                  (println (format "%s -- %2d threads [%.1f / s] %d executions, %.1f sec"
                                   f-name
                                   thread-count
                                   (/ processed seconds)
                                   processed
                                   seconds)))))))))
    (finally
      (shutdown-agents))))
