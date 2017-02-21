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
  (let [house (test/create-resource "house")]
    (set-relation house "owner" [123 456])
    (is (= [123 456] (get-relation house "owner")))))

(deftest get-non-existing-test
  (let [person (test/create-resource "person")]
    (doseq [path [nil, [], {}, ["no" "admission"]]]
      (is (thrown? IllegalArgumentException (get-attr person path)))
      (is (thrown? IllegalArgumentException (set-attr person path :value))))))

(deftest rendering-test
  (let [house (test/create-resource "house")]
    (set-attr house ["id"] :id)
    (set-attr house ["attributes" "size"] [1 2 3])
    (set-relation house "owner" :none)
    (is (= (debug-data house)
           {:attributes
            [{:index 0 :path ["attributes" "color"] :value nil}
             {:index 1 :path ["attributes" "size"] :value [1 2 3]}
             {:index 2 :path ["id"] :value :id}
             {:index 3 :path ["type"] :value nil}]
            :relations
            [{:index 0 :name "owner" :value :none}]}))))
