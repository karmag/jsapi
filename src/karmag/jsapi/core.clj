(ns karmag.jsapi.core
  (:require [clojure.java.io :as io]
            [karmag.jsapi.database :as db]
            [karmag.jsapi.definition :as d]
            [karmag.jsapi.json :as js]
            [karmag.jsapi.protocol :refer :all]
            [karmag.jsapi.resource :as r]
            [taoensso.carmine :as car :refer [wcar]]))

;; before reflections removed

;; Main running ...
;;
;; --------------------------------------------------
;; karmag.jsapi.core$_main$fn__5835
;;
;; Completed 100000 tasks in 0.8 seconds [121212.1 / s]
;;
;; --------------------------------------------------
;; karmag.jsapi.core$_main$write_resource_fast__5837
;;
;; Completed 100000 tasks in 3.5 seconds [28943.6 / s]
;;
;; --------------------------------------------------
;; karmag.jsapi.core$_main$write_data__5841
;;
;; Completed 100000 tasks in 1.2 seconds [86132.6 / s]
;;
;; --------------------------------------------------
;; karmag.jsapi.core$_main$parse_resource__5833
;;
;; Completed 100000 tasks in 20.7 seconds [4821.6 / s]
;;
;; --------------------------------------------------
;; clojure.core$comp$fn__4727
;;
;; Completed 100000 tasks in 24.3 seconds [4117.4 / s]

;; with reflection warnings fixed

;; Main running ...
;;
;; --------------------------------------------------
;; karmag.jsapi.core$_main$fn__5833
;;
;; Completed 100000 tasks in 0.8 seconds [120192.3 / s]
;;
;; --------------------------------------------------
;; karmag.jsapi.core$_main$write_resource_fast__5835
;;
;; Completed 100000 tasks in 3.9 seconds [25413.0 / s]
;;
;; --------------------------------------------------
;; karmag.jsapi.core$_main$write_data__5839
;;
;; Completed 100000 tasks in 1.2 seconds [82169.3 / s]
;;
;; --------------------------------------------------
;; karmag.jsapi.core$_main$parse_resource__5831
;;
;; Completed 100000 tasks in 3.4 seconds [29420.4 / s]
;;
;; --------------------------------------------------
;; clojure.core$comp$fn__4727
;;
;; Completed 100000 tasks in 6.1 seconds [16455.5 / s]


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
    (let [amount (long 100000)

          gen-id #(str (rand-int Integer/MAX_VALUE))

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

          fns [gen-resource
               write-resource-fast
               write-data
               parse-resource
               (comp write-resource parse-resource)]]

      (doseq [f fns]
        (println)
        (println "--------------------------------------------------")
        (println (type f))
        (let [start (System/currentTimeMillis)]
          (->> (repeat amount f)
               (pmap #(%))
               dorun)
          #_(dotimes [n amount]
              (f))
          (let [end (System/currentTimeMillis)
                secs (/ (- end start) 1000)]
            (println)
            (println (format "Completed %d tasks in %.1f seconds [%.1f / s]"
                             amount
                             (float secs)
                             (float (/ amount secs))))))))
    (finally
      (shutdown-agents))))
