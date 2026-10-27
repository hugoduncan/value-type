clojure -J--add-exports=java.base/jdk.internal.value=ALL-UNNAMED -J-XX:-UseArrayFlattening -M:test -m scry.cli --namespace value-type.array-test --namespace value-type.array-layout-test 
