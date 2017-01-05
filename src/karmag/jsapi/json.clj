(ns karmag.jsapi.json
  (:require [karmag.jsapi.util :refer [evaluate]]
            [karmag.jsapi.protocol :refer [set-attr]])
  (:import com.fasterxml.jackson.core.JsonFactory
           com.fasterxml.jackson.core.JsonParser
           com.fasterxml.jackson.core.JsonToken
           com.fasterxml.jackson.databind.util.TokenBuffer
           java.io.InputStream))

;; TODO fix
(def println (constantly nil))

  ;; 3  =>  JsonToken/START_ARRAY
  ;; 1  =>  JsonToken/START_OBJECT
  ;; 5  =>  JsonToken/FIELD_NAME
  ;; 4  =>  JsonToken/END_ARRAY
  ;; 2  =>  JsonToken/END_OBJECT

  ;; 9  =>  JsonToken/VALUE_TRUE
  ;; 10 =>  JsonToken/VALUE_FALSE
  ;; 11 =>  JsonToken/VALUE_NULL

  ;; 6  =>  JsonToken/VALUE_STRING
  ;; 8  =>  JsonToken/VALUE_NUMBER_FLOAT
  ;; 7  =>  JsonToken/VALUE_NUMBER_INT

  ;; -1 =>  JsonToken/NOT_AVAILABLE
  ;; 12 =>  JsonToken/VALUE_EMBEDDED_OBJECT

;;--------------------------------------------------
;; internals

(defprotocol JsonResource
  (populate-from-json [this parser]))

(defn- set-attr** [resource path value]
  (set-attr resource path value))

;;--------------------------------------------------
;; parser helpers

(defmacro ecase
  "Like case but but only handles enums as tests."
  [key & pairs]
  (let [pairs# (partition 2 pairs)
        default# (if (odd? (count pairs)) [(last pairs)] [])]
    `(case ~key
       ~@(mapcat (fn [[k v]] [(.id ^JsonToken (evaluate k)) v]) pairs#)
       ~@default#)))

(defn- next-token [parser]
  (let [token (.nextToken ^JsonParser parser)]
    (println "next-token ::" token)
    token))

(defn- post-step [parser value]
  (next-token parser)
  value)

(defn- step [parser]
  (println "step ::" (.nextToken ^JsonParser parser))
  parser)

;;--------------------------------------------------
;; parsing helpers

(defn- error [^JsonParser parser msg]
  (let [location (.getTokenLocation parser)
        token (.currentToken parser)]
    (throw (ex-info msg {:line (.getLineNr location)
                         :column (.getColumnNr location)
                         :byte-offset (.getByteOffset location)
                         :char-offset (.getCharOffset location)
                         :token token}))))

(defn- get-value
  ([^JsonParser parser]
   (get-value parser (.currentToken parser)))
  ([^JsonParser parser ^JsonToken token]
   (let [r__
         (ecase (.id token)
                JsonToken/VALUE_TRUE  true
                JsonToken/VALUE_FALSE false
                ;; TODO should null be handled differently in some way
                JsonToken/VALUE_NULL nil
                JsonToken/VALUE_NUMBER_FLOAT (.getDecimalValue parser)
                ;; TODO use different number types?
                JsonToken/VALUE_NUMBER_INT (.getBigIntegerValue parser)
                JsonToken/VALUE_STRING (.getValueAsString parser))]
     (println "get-value ::" r__)
     r__)))

(defn- parse-object
  "Calls (f parser state key-name) on each key-value pair in the
  object. f is expected to consume value but no more than that."
  [^JsonParser parser f state]
  (if (= JsonToken/START_OBJECT (.currentToken parser))
    (do (step parser)
        (loop [state state]
          (println "parse-object loop:" state (.currentToken parser))
          (ecase (.id (.currentToken parser))
                 JsonToken/FIELD_NAME
                 (let [current-name (.getCurrentName parser)
                       state (f (step parser) state current-name)]
                   (recur state))
                 JsonToken/END_OBJECT
                 (do (next-token parser)
                     state))))
    (error parser "Expected object")))

(defn- parse-string [^JsonParser parser]
  (let [token (.currentToken parser)]
    (if (= JsonToken/VALUE_STRING token)
      (post-step parser (get-value parser token))
      (error parser "Expected string"))))

(defn- parse-int [^JsonParser parser]
  (let [token (.currentToken parser)]
    (if (= JsonToken/VALUE_NUMBER_INT token)
      (post-step parser (get-value parser token))
      (error parser "Expected int"))))

;; TODO impl
(defn- parse-timestamp [parser]
  (error parser "parse-timestamp not implemented"))

;;--------------------------------------------------
;; resource parse generation

(defn- prepare-attr-data [resource-def]
  (let [find-type (fn [attr]
                    (or (first (map #{:string :int} (:constraints attr)))
                        :string))]
    (->> (:attributes resource-def)
         (map (fn [attr]
                {:type (find-type attr)
                 :path (:path attr)
                 :attribute? true}))
         (reduce (fn [m attr]
                   (assoc-in m (:path attr) attr))
                 nil))))

(defn- mk-populate-from-attrs [attr-data]
  (list parse-object
        'parser
        (list 'fn ['parser 'state 'key-name]
              (concat (list 'case 'key-name)
                      (mapcat (fn [[k {:keys [attribute? type path] :as value}]]
                                [k (if attribute?
                                     (list set-attr** 'state path
                                           (list (case type
                                                   :string parse-string
                                                   :int parse-int)
                                                 'parser))
                                     (mk-populate-from-attrs value))])
                              attr-data)
                      [(list error 'parser
                             (list 'str "Unknown key '" 'key-name "'"))])
              'state)
        'state))

(defn- mk-resource-parser [resource-def]
  ;; TODO should expose functions in resource.clj for building these
  ;; type of symbols/values
  (let [type (evaluate (symbol (str "karmag.jsapi.resource." (:type resource-def))))
        attr-data (prepare-attr-data resource-def)]
    (extend type
      JsonResource
      {:populate-from-json
       (binding [*ns* (create-ns (symbol "karmag.jsapi.json"))]
         (evaluate (list 'fn ['state 'parser]
                         (mk-populate-from-attrs attr-data))))})))

;;--------------------------------------------------
;; parsing

(defn- parse-resource [^JsonParser parser create-resource]
  (println "parse-resource")
  (let [[resource-type ^TokenBuffer token-buffer]
        (parse-object parser
                      (fn [^JsonParser parser [type buffer] key-name]
                        (let [^TokenBuffer buffer (or buffer
                                                      (doto (TokenBuffer. parser)
                                                        (.writeStartObject)))]
                          (if (= key-name "type")
                            (let [type (parse-string parser)]
                              (.writeFieldName buffer ^String key-name)
                              (.writeString buffer ^String type)
                              [type buffer])
                            (do (.writeFieldName buffer ^String key-name)
                                (.copyCurrentStructure buffer parser)
                                (next-token parser)
                                [type buffer]))))
                      ;; TODO maybe use array here as well?
                      [nil nil])]
    (when-not resource-type
      (error parser "Missing type attribute for resource"))
    (if token-buffer
      (.writeEndObject token-buffer)
      (error parser "Resource data missing"))
    (println "Parsing cached token info")
    (let [resource (create-resource resource-type)
          buffered-parser (.asParser token-buffer parser)]
      (.nextToken buffered-parser)
      (populate-from-json resource buffered-parser))))

(defn- parse-jsonapi-document [parser create-resource]
  (println "parse-jsonapi-document")
  (parse-object parser
                (fn [parser state key-name]
                  (case key-name
                    "data" (assoc state :data (parse-resource
                                               parser
                                               create-resource))))
                {:data [], :included []}))

;;--------------------------------------------------
;; stuff

(defn make [definition create-resource]
  (doseq [resource-def (:resources definition)]
    (mk-resource-parser resource-def))
  (let [factory (JsonFactory.)]
    (fn [jsonable]
      (with-open [^JsonParser parser
                  (if (instance? InputStream jsonable)
                    (.createJsonParser factory ^InputStream jsonable)
                    (if (string? jsonable)
                      (.createJsonParser factory ^String jsonable)
                      (throw (ex-info
                              (str "No implementation for handling " (type jsonable))
                              {}))))]
        (next-token parser)
        (parse-jsonapi-document parser create-resource)))))
