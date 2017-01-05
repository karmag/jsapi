(ns karmag.jsapi.test
  (:require [clojure.java.io :as io]
            [karmag.jsapi.database :as db]
            [karmag.jsapi.definition :as d]
            [karmag.jsapi.json :as js]
            [karmag.jsapi.resource :as r]))

(def definition
  (-> "test-definition.clj" io/resource d/read-definition))

(def create-resource
  (r/make definition))

(def parse-json
  (js/make definition create-resource))

(def database
  (db/make "localhost" 6379 create-resource))
