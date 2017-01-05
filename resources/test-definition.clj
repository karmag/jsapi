#def [:id {:path ["id"], :constraints [:string]}]
#def [:type {:path ["type"], :constraints [[:const #arg :type]]}]

#def
[:person
 #karmag.jsapi/resource
 {:type "person"
  :attributes [#ref :id
               #ref [:type {:type "person"}]
               {:path ["attributes" "name"]
                :constraints [:string [:range :min 2 :max 50]]}
               {:path ["attributes" "prio"]
                :constraints [:int [:range :min 0 :max 2147483647]]}
               {:path ["attributes" "valid-for" "start"]
                :constraints [:timestamp]}
               {:path ["attributes" "valid-for" "end"]
                :constraints [:timestamp]}
               {:path ["meta" "version"]
                :constraints [:int]}]}]

#ref :person

#def
[:car
 #karmag.jsapi/resource
 {:type "car"
  :attributes [#ref :id
               #ref [:type {:type "car"}]
               {:path ["attributes" "model"]
                :constraints [:string]}]
  :relations [{:target #{"person"}
               :name "owner"
               :constraints [[:range :min 1 :max 1]]}]}]

#ref :car

#karmag.jsapi/context
{:tag {:operation :create
       :direction :request}
 :resource #ref :person
 :mods [[:remove-attr ["meta" "version"]]]}
