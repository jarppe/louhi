(ns louhi.middleware.errors
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [louhi.http.status :as status]))


(set! *warn-on-reflection* true)



(defn wrap-handle-exceptions [handler]
  (fn [req]
    (try
      (handler req)
      (catch clojure.lang.ExceptionInfo e
        (let [ex (ex-data e)]
          (if (-> ex :type (= :ring.util.http-response/response))
            (-> ex :response)
            (throw e)))))))


(def default-error-resp {:status  status/internal-server-error
                         :headers {"content-type" "text/plain; charset=utf-8"}
                         :body    "Unexpected error"})


(defn wrap-handle-errors
  ([handler] (wrap-handle-errors handler default-error-resp))
  ([handler error-resp]
   (let [error-resp (if (and (map? error-resp) (:status error-resp))
                      (constantly error-resp)
                      error-resp)]
     (fn [req]
       (try
         (handler req)
         (catch Exception e
           (error-resp e)))))))
