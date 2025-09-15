(ns louhi.server.core)


(set! *warn-on-reflection* true)


(deftype Server [impl close port status]
  java.io.Closeable
  (close [_] (close))

  clojure.lang.IMeta
  (meta [_]
    {:impl   impl
     :status (status)
     :port   (port)})

  clojure.lang.Named
  (getNamespace [_] (namespace impl))
  (getName [_] (name impl)))


(defn server [{:keys [impl port status close]}]
  (->Server impl
            close
            (or port (constantly -1))
            (or status (constantly "?"))))


(defmethod print-method Server [server ^java.io.Writer w]
  (.append w (format "louhi.server.core/Server[impl=%s, status=%s, port=%s]"
                     (-> server (meta) :impl)
                     (-> server (meta) :status)
                     (-> server (meta) :port))))
