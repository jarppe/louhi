(ns louhi.util.cache)


(set! *warn-on-reflection* true)

;;
;; Middleware to set default Cache-Control on responses. If response contains Cache-Control, 
;; does nothing. If not, then sets Cache-Control to default value. The default for responses 
;; with ETag is "no-cache" and responses without ETag "no-store".
;;
;; Note that "no-cache" does not mean that client should not cache responses. For more info on 
;; "no-cache" see https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Cache-Control#no-cache
;;


(def wrap-cache-headers
  {:name :louhi/cache-headers
   :wrap (fn [handler]
           (fn [req]
             (let [resp         (handler req)
                   resp-headers (:headers resp)]
               (when resp
                 (-> (if (contains? resp-headers "cache-control")
                       resp
                       (update resp :headers assoc
                               "cache-control" (if (or (-> resp-headers (contains? "etag"))
                                                       (-> resp :status (= 304)))
                                                 "private, no-cache, max-age=3600"
                                                 "no-store")
                               "vary" "cookie, hx-request")))))))})
