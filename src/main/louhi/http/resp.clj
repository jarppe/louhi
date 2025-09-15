(ns louhi.http.resp
  (:require [louhi.http.status :as status]))


(set! *warn-on-reflection* true)


(def ^:private default-message
  {status/bad-request           "Bad request"
   status/unauthorized          "Authorization required"
   status/forbidden             "Forbidden"
   status/not-found             "Not found"
   status/internal-server-error "Internal server error"})


(defn error! [{:keys [ex-message status body cause]}]
  (throw (ex-info (or ex-message "unexpected error")
                  {:type     :ring.util.http-response/response
                   :response {:status  (or status status/internal-server-error)
                              :headers {"content-type" "text/plain; charset=utf-8"}
                              :body    (or body (default-message status "Unexpected error"))}}
                  cause)))
