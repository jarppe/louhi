(ns louhi.middleware.session 
  (:require [louhi.http.status :as status]))


(defn wrap-require-session [handler]
  (fn [req]
    (if (-> req :louhi.session/session (some?))
      (handler req)
      {:status  status/unauthorized
       :headers {"content-type" "text/plain; charset=utf-8"}
       :body    "Session required"})))
