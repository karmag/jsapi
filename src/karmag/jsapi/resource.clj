(ns karmag.jsapi.resource
  (:require [karmag.jsapi.protocol :refer :all]
            [karmag.jsapi.util :refer [evaluate array-get array-set]]))

(def ^:private ATTRIBUTES 0)
(def ^:private RELATIONS 1)

(defn- get-data** [x] (get-data x))

;;--------------------------------------------------
;; code gen

(defn- get-attributes
  "Returns ordered attributes."
  [resource]
  (->> (:attributes resource)
       (sort-by #(->> % :path (interpose "|") (apply str)))))

(defn- get-relations
  "Returns ordered relations."
  [resource]
  (->> (:relations resource) (sort-by :name)))

(defn- make-get-attr-def [resource]
  (let [attr-fetch (->> (get-attributes resource)
                        (map :path)
                        (map-indexed (fn [index path]
                                       [path (list array-get
                                                   (list get-data** 'res)
                                                   ATTRIBUTES
                                                   index)]))
                        (mapcat identity))]
    (list 'get-attr ['res 'path]
          (apply list 'case 'path attr-fetch))))

(defn- make-set-attr-def [resource]
  (let [attr-setters (->> (get-attributes resource)
                          (map :path)
                          (map-indexed (fn [index path]
                                         [path (list array-set
                                                     (list get-data** 'res)
                                                     ATTRIBUTES
                                                     index
                                                     'value)]))
                          (mapcat identity))]
    (list 'set-attr ['res 'path 'value]
          (apply list 'case 'path attr-setters))))

(defn- make-get-relation-def [resource]
  (let [relation-fetch (->> (get-relations resource)
                            (map :name)
                            (map-indexed (fn [index name]
                                           [name (list array-get
                                                       (list get-data** 'res)
                                                       RELATIONS
                                                       index)]))
                            (mapcat identity))]
    (list 'get-relation ['res 'name]
          (apply list 'case 'name relation-fetch))))

(defn- make-set-relation-def [resource]
  (let [relation-setters (->> (get-relations resource)
                              (map :name)
                              (map-indexed (fn [index name]
                                             [name (list array-set
                                                         (list get-data** 'res)
                                                         RELATIONS
                                                         index
                                                         'value)]))
                              (mapcat identity))]
    (list 'set-relation ['res 'name 'value]
          (apply list 'case 'name relation-setters))))

(defn- make-debug-render-def [resource]
  (let [attrs (->> (get-attributes resource)
                   (map :path)
                   (map-indexed (fn [index path]
                                  {:index index
                                   :path path}))
                   vec)
        get-fn (fn [res attr]
                 (assoc attr :value (get-attr res (:path attr))))
        relations (->> (get-relations resource)
                       (map :name)
                       (map-indexed (fn [index name]
                                      {:index index
                                       :name name}))
                       vec)
        get-rel-fn (fn [res rel]
                     (assoc rel :value (get-relation res (:name rel))))]
    (list 'debug-data ['res]
          {:attributes (list doall (list map (list partial get-fn 'res) attrs))
           :relations (list doall (list map (list partial get-rel-fn 'res) relations))})))

;;--------------------------------------------------
;; creation

(defn- make-type [resource]
  (binding [*ns* (create-ns 'karmag.jsapi.resource)]
    (evaluate
     (list 'deftype (symbol (:type resource)) ['data]
           'karmag.jsapi.protocol.DataFetch
           (list 'get-data ['this] 'data)
           'karmag.jsapi.protocol.ResourceAttributes
           (make-get-attr-def resource)
           (make-set-attr-def resource)
           'karmag.jsapi.protocol.ResourceRelations
           (make-get-relation-def resource)
           (make-set-relation-def resource)
           'karmag.jsapi.protocol.Debug
           (make-debug-render-def resource)))))

;; TODO creator should set type automatically, maybe disallow setting
;; of type manually?
(defn- make-creator [resource-coll]
  (let [counted (map (fn [res]
                       [(:type res)
                        (count (:attributes res))
                        (count (:relations res))])
                     resource-coll)]
    (evaluate
     (list 'fn 'create-resource ['name]
           (apply list 'case 'name
                  (mapcat (fn [[type attr-count rel-count]]
                            [type (list (symbol (str "karmag.jsapi.resource/->" type))
                                        (list object-array
                                              [(list object-array attr-count)
                                               (list object-array rel-count)]))])
                          counted))))))

(defn make [definition]
  (doseq [resource (:resources definition)]
    (make-type resource))
  (make-creator (:resources definition)))
