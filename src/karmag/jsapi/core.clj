(ns karmag.jsapi.core
  (:require [clojure.java.io :as io]
            [karmag.jsapi.database.cassandra :as db]
            [karmag.jsapi.definition :as d]
            [karmag.jsapi.json :as js]
            [karmag.jsapi.protocol :refer :all]
            [karmag.jsapi.resource :as r]
            [taoensso.carmine :as car :refer [wcar]])
  (:import java.util.concurrent.Semaphore))

(def definition
  (-> "resource-definition.clj" io/resource d/read-definition))

(def create-resource
  (r/make definition))

(def parse-json
  (js/make definition create-resource))

(def database
  (db/make "localhost" 9042 definition create-resource))

(comment
  (try
    (doseq [x (db/create-schema (:resources definition))]
      (println x))
    (catch Exception e
      (.printStackTrace e))
    (finally
      (flush)
      (Thread/sleep 1000)
      (System/exit 0))))

(def js-data "{
  \"data\": {
    \"type\": \"person\",
    \"id\": \"hello123\",
    \"attributes\": {
      \"name\": \"karl\"
    }
  }
}")

;;--------------------------------------------------
;; measuring

(def m-data (atom {}))

(defn add-time [id time]
  (swap! m-data update-in [id] conj time))

(defn get-times [id]
  (get @m-data id))

(defmacro measure [id & body]
  `(let [start# (System/nanoTime)]
     (try
       ~@body
       (finally
         (let [end# (System/nanoTime)]
           (add-time ~id (- end# start#)))))))

(defn nice-digit [n]
  (->> n str reverse (partition-all 3) (interpose [","])
       (mapcat identity) reverse (apply str)))

(defn show [id]
  (let [values (get-times id)]
    (if (empty? values)
      (println (str "Nothing with id " id))
      (let [values (sort values)
            min-time (first values)
            max-time (last values)
            avg (long (/ (reduce + values)
                         (count values)))
            perseent (fn [prs]
                       (let [below (take (long (* prs (count values))) values)]
                         (format "%4.1f%% under %s ns"
                                 (float (* 100 prs))
                                 (nice-digit (last below)))))]
        (println "Time (ns) for" id
                 ":: min:" (nice-digit min-time)
                 " max:" (nice-digit max-time)
                 " avg:" (nice-digit avg))
        (doseq [prs [1/2 3/4 9/10 99/100 999/1000]]
          (println " " (perseent prs)))))))

(defn reset-register! []
  (reset! m-data {}))

;;--------------------------------------------------
;; locking

(def lock-amount 100)
(def locks (->> #(Semaphore. 1) repeatedly (take lock-amount) vec))
(defmacro with-lock [obj & body]
  `(let [index# (mod (.hashCode ^Object ~obj) lock-amount)
         lock# (get locks index#)]
     (try
       (.acquire ^Semaphore lock#)
       ~@body
       (finally
         (.release ^Semaphore lock#)))))

(defn -main []
  (println "Main running ...")
  (try
    (let [gen-id #(str (rand-int Integer/MAX_VALUE))

          parse-resource (fn []
                           (measure "parse-resource"
                                    (let [res (:data (parse-json js-data))]
                                      (set-attr res ["id"] (gen-id))
                                      res)))

          gen-resource (let [res (parse-resource)]
                         #(do (set-attr res ["id"] (gen-id))
                              res))

          write-resource-fast (fn [] (save-resource database (gen-resource)))
          write-resource (fn [res]
                           (measure "db-save"
                                    (save-resource database res)))
          write-data (fn [] (wcar {:host "localhost" :port 6379}
                                  (car/set (gen-id) "data goes here")))

          fns [#_["small write" write-data]
               #_["resource write" write-resource-fast]
               #_["resource parse" parse-resource]
               ["parse + write" (fn []
                                  (measure "parse + write"
                                           (write-resource (parse-resource))))]]]

      (doseq [[f-name f] fns]
        (doseq [thread-count [50 100 200 300]]
          (reset-register!)

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
              (Thread/sleep 10000)
              (let [end-count @counter
                    end-time (System/currentTimeMillis)]
                (reset! running false)
                (doseq [t threads] (.join ^Thread t))
                (let [processed (- end-count start-count)
                      seconds (float (/ (- end-time start-time)
                                        1000))]
                  (println (format "%s -- %2d threads [%.1f / s] %s executions, %.1f sec"
                                   f-name
                                   thread-count
                                   (/ processed seconds)
                                   (nice-digit processed)
                                   seconds))
                  (show "parse-resource")
                  (show "db-save")
                  (show "parse + write")
                  (println))))))))
    (System/exit 0)
    (finally
      (shutdown-agents))))
