(ns louhi.middleware.security-headers
  (:require [louhi.http.security-headers :as sh]))


(defn wrap-security-headers [handler]
  (fn [req]
    (when-let [resp (handler req)]
      (update resp :headers merge sh/security-headers))))
