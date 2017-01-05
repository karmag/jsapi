(ns karmag.jsapi.protocol)

;;--------------------------------------------------
;; resource

(defprotocol DataFetch
  (get-data [this]))

(defprotocol ResourceAttributes
  (get-attr [this path])
  (set-attr [this path value]))

(defprotocol ResourceRelations
  (get-relation [this name])
  (set-relation [this name relations]))

(defprotocol Debug
  (debug-data [this]))

;;--------------------------------------------------
;; database

(defprotocol DatabaseSession
  (load-resource [this type id])
  (save-resource [this resource]))
