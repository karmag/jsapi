(ns karmag.jsapi.database
  (:require [karmag.jsapi.protocol :refer :all]
            [karmag.jsapi.util :refer [array-get]]
            [taoensso.carmine :as car :refer [wcar]]))

(defrecord RedisDatabase [config create-resource]
  DatabaseSession
  (load-resource [this type id]
    (when-let [stored (wcar config (car/get (str type ":" id)))]
      (let [resource (create-resource type)
            data (get-data resource)]
        (dotimes [major (count data)]
          (dotimes [minor (count (array-get data major))]
            (aset data major minor (aget stored major minor))))
        resource)))
  (save-resource [this resource]
    (let [data (get-data resource)
          type (get-attr resource ["type"])
          id (get-attr resource ["id"])]
      (wcar config (car/set (str type ":" id) data)))))

(defn make [host port create-resource]
  (RedisDatabase. {:host host :port port} create-resource))
