(ns karmag.jsapi.database.cassandra
  (:require [clojure.string :as s]
            [karmag.jsapi.protocol :refer :all]
            [karmag.jsapi.util :refer [evaluate]])
  (:import com.datastax.driver.core.Cluster))

;; UPDATE <type> SET id = ?, attributes__name = ? WHERE id = ?;
;; SELECT id, attributes__name FROM <type> WHERE id = ?;

(defn- dbilize [name]
  (s/replace name "-" "_"))

(defn- get-attr** [res path] (get-attr res path))
(defn- set-attr** [res path value] (set-attr res path value))

;;--------------------------------------------------
;; statements

(defn- attributes [resource-def]
  (->> (:attributes resource-def)
       (map (fn [attr]
              (assoc attr
                     :type (or (:type attr) :string)
                     :db-name (dbilize (s/join "__" (:path attr))))))
       (sort-by :db-name)
       (map-indexed (fn [index attr] (assoc attr :index index)))
       doall))

(defn- update-query [resource-def]
  (format "UPDATE %s SET %s WHERE id = ?"
          (dbilize (:type resource-def))
          (->> (attributes resource-def)
               (map :db-name)
               (remove #{"id"})
               (map #(str % " = ?"))
               (s/join ", "))))

(defn- select-query [resource-def]
  (format "SELECT %s FROM %s WHERE id = ?"
          (->> (attributes resource-def)
               (map :db-name)
               ;;(remove #{"id"})
               (s/join ", "))
          (dbilize (:type resource-def))))

;;--------------------------------------------------
;; schema

(def ^:private keyspace "ks")

(defn- get-attribute-type [attribute]
  (case (or (:type attribute) :string)
    :string "text"
    :int "bigint"
    :timestamp "timestamp"
    (throw (ex-info (str "No attribute type from "
                         (vec (:constraints attribute)))
                    {}))))

(defn- create-attribute-schema [attribute]
  (let [path (:path attribute)
        type (get-attribute-type attribute)
        column (dbilize (s/join "__" path))]
    (if (= column "id") ;; TODO not hardcode this
      (format "%s %s PRIMARY KEY" column type)
      (format "%s %s" column type))))

(defn- create-resource-schema [resource-def]
  (format "CREATE TABLE IF NOT EXISTS %s.%s (%s);"
          keyspace
          (:type resource-def)
          (s/join ", " (map create-attribute-schema
                            (:attributes resource-def)))))

(defn- create-schema [resource-coll]
  (cons (format "CREATE KEYSPACE IF NOT EXISTS %s WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 1 };"
                keyspace)
        (map create-resource-schema resource-coll)))

;;--------------------------------------------------
;; resource <-> db

(defprotocol DatabaseResource
  (build-update-query [this])
  (build-select-query [this])
  (write-resource [this statement])
  (parse-resource [this row]))

(defn- mk-write-resource [attributes]
  (apply list 'fn ['resource 'statement]
         (let [id (filter #(= "id" (:db-name %)) attributes)
               other (remove #(= "id" (:db-name %)) attributes)
               attributes (map-indexed (fn [index attr]
                                         (assoc attr :index index))
                                       (concat other id))]
           (->> attributes
                (map (fn [attr-def]
                       (list 'when-let ['value
                                        (list 'get-attr** 'resource (:path attr-def))]
                             (case (:type attr-def)
                               :string (list '.setString 'statement (:db-name attr-def) 'value)
                               :int (list '.setInt 'statement (:db-name attr-def) 'value)
                               nil))))))))

(defn- mk-parse-resource [attributes]
  (apply list 'fn ['resource 'row]
         (->> attributes
              (map (fn [attr-def]
                     (list 'when-let ['value
                                      (case (:type attr-def)
                                        :string (list '.getObject 'row (:db-name attr-def))
                                        :int (list '.getObject 'row (:db-name attr-def))
                                        nil)]
                           (list 'set-attr** 'resource (:path attr-def) 'value)))))))

(defn- mk-database-resource [resource-def]
  (let [type (evaluate (symbol (str "karmag.jsapi.resource." (:type resource-def))))
        attr-data (attributes resource-def)]
    (extend type
      DatabaseResource
      (binding [*ns* (create-ns (symbol "karmag.jsapi.database.cassandra"))]
        {:build-update-query (constantly (update-query resource-def))
         :build-select-query (constantly (select-query resource-def))
         :write-resource (evaluate (mk-write-resource attr-data))
         :parse-resource (evaluate (mk-parse-resource attr-data))}))))

;;--------------------------------------------------
;; interface

(defrecord CassandraDatabase [cluster session create-resource]
  DatabaseSession
  (load-resource [this type id]
    (let [resource (create-resource type)
          ps (.prepare session (build-select-query resource))
          bs (doto (.bind ps)
               (.setString "id" id))
          result-set (.execute session bs)
          row (.one result-set)]
      (when row
        (parse-resource resource row)
        resource)))
  (save-resource [this resource]
    (let [ps (.prepare session (build-update-query resource))
          bs (.bind ps)]
      (write-resource resource bs)
      (.execute session bs))))

(defn setup-schema [host port definition]
  (with-open [cluster (.. Cluster builder (addContactPoint host) build)
              session (.connect cluster)]
    (doseq [query (create-schema (:resources definition))]
      (println "Setup:" query)
      (.execute session query))))

(defn make [host port definition create-resource]
  (doseq [resource-def (:resources definition)]
    (mk-database-resource resource-def))
  (let [cluster (.. Cluster builder (addContactPoint host) build)
        session (.connect cluster "ks")]
    (CassandraDatabase. cluster session create-resource)))
