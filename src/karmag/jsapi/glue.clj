(ns karmag.jsapi.glue
  (:require [clojure.java.io :as io]
            [karmag.jsapi.database :as db]
            [karmag.jsapi.definition :as d]
            [karmag.jsapi.http :as http]
            [karmag.jsapi.json :as js]
            [karmag.jsapi.protocol :refer :all]
            [karmag.jsapi.resource :as r])
  (:import org.apache.http.entity.StringEntity))

;; parse request
;; - find dispatch path (method + uri)
;; - potentially extract data (POST / UPDATE)

;; POST
;; - write to db (SETNX)

;; GET
;; - get from db (GET)

;; UPDATE
;; - write to db (SET)

(defn- get-resource [request response database]
  (let [uri (.. request getRequestLine getUri)
        [_ _ type id] (.split uri "/")]
    (if (and type id)
      (if-let [resource (load-resource database type id)]
        (let [id (get-attr resource ["id"])]
          (.setStatusCode response 200)
          (.setEntity response (StringEntity. (str "hello: " id))))
        (do (.setStatusCode response 404)))
      (println "Missing resource/id part in uri"))))

(defn- post-resource [request response parse-json database]
  (let [entity (.getEntity request)
        jsonapi-data (parse-json (.getContent entity))
        resource (:data jsonapi-data)]
     (println "---" (.getContentLength entity) (debug-data resource))
    (save-resource database resource)
    (.setStatusCode response 222)))

(defn- make-handler [parse-json database]
  (fn [request response context]
    (try
      (case (.. request getRequestLine getMethod toUpperCase)
        "GET" (get-resource request response database)
        "POST" (post-resource request response parse-json database)
        (throw (ex-info (str (.. request getRequestLine getMethod toUpperCase)
                             " method not suppored")
                        {})))
      (catch Exception e
        (.printStackTrace e)
        (.setStatusCode response 500)))))

(defn start
  ([definition]
   (start definition
          (r/make definition)))
  ([definition create-resource]
   (start definition
          create-resource
          (js/make definition create-resource)))
  ([definition create-resource parse-json]
   (start definition
          create-resource
          parse-json
          (db/make "localhost" 6379 create-resource)))
  ([definition create-resource parse-json database]
   (http/start 9999 (make-handler parse-json database))))
