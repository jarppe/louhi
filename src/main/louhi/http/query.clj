(ns louhi.http.query
  (:import (java.net URLDecoder)))


(set! *warn-on-reflection* true)


(def ^:private ^:const char= (int \=))


(defn parse-query-string [query-string]
  (let [t (java.util.StringTokenizer. (or query-string "") "&")]
    (loop [query {}]
      (if (.hasMoreTokens t)
        (let [kv  (.nextToken t)
              sep (.indexOf kv char=)]
          (recur (if (= sep -1)
                   (assoc query
                          (URLDecoder/decode kv)
                          nil)
                   (assoc query
                          (URLDecoder/decode (.substring kv 0 sep))
                          (URLDecoder/decode (.substring kv (inc sep)))))))
        query))))