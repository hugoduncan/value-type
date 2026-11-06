(require 'spike.use)
(in-ns 'spike.use)
(let [a (->Rect 2 3.5 :x) b (->Rect 2 3.5 :x) c (->Rect 2 3.5 (vector 1)) d (->Rect 2 3.5 (vector 1))]
  (println "isValue" (.isValue Rect) "flags" (.accessFlags Rect)
           "fieldflags" (.accessFlags (.getDeclaredField Rect "w")))
  (println "area" (area a) "str" (str a) "invoke" (a))
  (println "identical?" (identical? a b) "=" (= a b) "hash=" (= (hash a) (hash b)))
  (println "vector fields not same object: identical?" (identical? c d) "=" (= c d))
  (println "locking:" (try (locking a :ok) (catch Throwable t (str (class t))))))
