(ns louhi.server.core)


(set! *warn-on-reflection* true)


(defrecord Server [server impl close port status]
  java.io.Closeable
  (close [_] (close)))


(defn server [{:keys [server impl port status close]
               :or   {port   -1
                      status (constantly "?")}}]
  (->Server server
            impl
            close
            port
            status))


(defmethod print-method Server [server ^java.io.Writer w]
  (.append w (format "louhi.server.core/Server[impl=%s, status=%s, port=%s]"
                     (-> server :impl)
                     ((-> server :status))
                     (-> server :port))))
