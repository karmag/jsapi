(ns karmag.jsapi.util
  (:require [clojure.pprint :refer [pprint]]))

(defn evaluate [form]
  (clojure.pprint/with-pprint-dispatch clojure.pprint/code-dispatch
    (println (str "eval in namespace: " *ns*))
    (pprint form)
    (println))
  (eval form))

;;--------------------------------------------------
;; arrays

(defn array-get
  ([array index] (aget ^"[Ljava.lang.Object;" array index))
  ([array i1 i2] (aget ^"[Ljava.lang.Object;" array i1 i2)))

(defn array-set
  ([array index value] (aset ^"[Ljava.lang.Object;" array index value))
  ([array i1 i2 value] (aset ^"[Ljava.lang.Object;" array i1 i2 value)))
