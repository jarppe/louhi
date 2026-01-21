(ns louhi.middleware.cache-control)


(set! *warn-on-reflection* true)


;;
;; Middleware to set default Cache-Control on responses. If response contains Cache-Control,
;; does nothing. If not, then sets Cache-Control to header to provided value. The default
;; value for cache-control is "private, no-store".
;;


(defn wrap-cache-headers
  ([handler] (wrap-cache-headers handler nil))
  ([handler {:keys [cache-control vary]}]
   (let [default-cache-control (or cache-control "private, no-store")
         default-vary          (or vary "cookie, hx-request, x-alpine-request")]
     (fn [req]
       (when-let [resp (handler req)]
         (if (-> resp :headers (contains? "cache-control"))
           resp
           (update resp :headers assoc
                   "cache-control" default-cache-control
                   "vary"          default-vary)))))))
