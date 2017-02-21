#def [:id ]
#def [:type {:path ["type"], :const #arg :type}]

#def
[:define-resource
 #karmag.jsapi/resource
 {:type #arg :type
  :attributes #code(into [{:path ["id"], :type :string}
                           {:path ["type"], :const #arg :type}
                           {:path ["meta" "version"], :type :int}]
                          #arg :attributes)
  :relations #code(into []
                        #arg :relations)}
 {:attributes []
  :relations []}]

#ref
[:define-resource
 {:type "person"
  :attributes [{:path ["attributes" "name"], :type :string}
               {:path ["attributes" "prio"], :type :int}]}]

#ref
[:define-resource
 {:type "house"
  :attributes [{:path ["attributes" "size"], :type :int}
               {:path ["attributes" "color"], :type :string}]
  :relations [{:target #{"person"}
               :name "owner"}]}]
