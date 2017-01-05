(ns karmag.jsapi.database-test
  (:require [clojure.test :refer :all]
            [karmag.jsapi.protocol :refer :all]
            [karmag.jsapi.test :as test]))

(deftest save-load-database-test
  (let [resource (test/create-resource "person")
        id (str "abc" (rand-int Integer/MAX_VALUE))]
    (set-attr resource ["type"] "person")
    (set-attr resource ["id"] id)
    (save-resource test/database resource)
    (let [stored (load-resource test/database "person" id)]
      (is (= (debug-data resource)
             (debug-data stored)))
      (is (not (identical? resource stored)))
      (is (not (identical? (get-data resource) (get-data stored)))))))

(deftest load-non-existing-test
  (is (nil? (load-resource test/database "person" "nothing"))))
