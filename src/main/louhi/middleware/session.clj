(ns louhi.middleware.session
  (:require [ring.util.http-response :as resp]))


(defn wrap-require-session [handler]
  (fn [req]
    (if (-> req :louhi.session/session (some?))
      (handler req)
      (resp/unauthorized "Session requitred"))))
