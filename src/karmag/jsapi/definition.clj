(ns karmag.jsapi.definition
  (:require [karmag.datum.core :as datum]))

(defn read-definition [rdr]
  (let [[data report] (datum/process rdr)]
    (when-not (empty? report)
      (throw (ex-info "Config report contains errors"
                      {:report report})))
    {:resources (->> (filter (comp #{'karmag.jsapi/resource} first) data)
                     (map second))
     :context (->> (filter (comp #{'karmag.jsapi/context} first) data)
                   (map second))}))
