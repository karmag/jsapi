(ns karmag.jsapi.test
  (:require [clojure.java.io :as io]
            [karmag.jsapi.database.cassandra :as db]
            [karmag.jsapi.definition :as d]
            [karmag.jsapi.json :as js]
            [karmag.jsapi.resource :as r]))

(def definition
  (-> "test-definition.clj" io/resource d/read-definition))

(def create-resource
  (r/make definition))

(def parse-json
  (js/make definition create-resource))

;; (def database.redis
;;   (db/make "localhost" 6379 create-resource))

;; (db/setup-schema "localhost" 9042 definition)

(def database.cassandra
  (db/make "localhost" 9042 definition create-resource))
