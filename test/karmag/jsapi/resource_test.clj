(ns karmag.jsapi.resource-test
  (:require [clojure.test :refer :all]
            [karmag.jsapi.protocol :refer :all]
            [karmag.jsapi.test :as test]))

(deftest get-set-test
  (let [person (test/create-resource "person")
        path ["attributes" "name"]]
    (is (nil? (get-attr person path)))
    (set-attr person path "First Lastsson")
    (is (= "First Lastsson") (get-attr person path))))

(deftest get-set-relations-test
  (let [car (test/create-resource "car")]
    (set-relation car "owner" [123 456])
    (is (= [123 456] (get-relation car "owner")))))

(deftest get-non-existing-test
  (let [person (test/create-resource "person")]
    (doseq [path [nil, [], {}, ["no" "admission"]]]
      (is (thrown? IllegalArgumentException (get-attr person path)))
      (is (thrown? IllegalArgumentException (set-attr person path :value))))))

(deftest rendering-test
  (let [car (test/create-resource "car")]
    (set-attr car ["id"] :id)
    (set-attr car ["attributes" "model"] [1 2 3])
    (set-relation car "owner" :none)
    (is (= (debug-data car)
           {:attributes
            [{:index 0 :path ["attributes" "model"] :value [1 2 3]}
             {:index 1 :path ["id"] :value :id}
             {:index 2 :path ["type"] :value nil}]
            :relations
            [{:index 0 :name "owner" :value :none}]}))))
