(ns karmag.jsapi.json-test
  (:require [clojure.test :refer :all]
            [karmag.jsapi.protocol :refer :all]
            [karmag.jsapi.test :as test]))

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

(use 'clojure.pprint)

(deftest create-parser-test
  (let [result (test/parse-json js-data)
        resource (:data result)]
    (are [path value] (= value (get-attr resource path))
         ["type"] "person"
         ["id"] "hello123"
         ["attributes" "name"] "karl"
         ["attributes" "prio"] 5
         ["attributes" "valid-for" "start"] "tomorrow"
         ["meta" "version"] 1230)
    (pprint (debug-data (-> result :data)))))
