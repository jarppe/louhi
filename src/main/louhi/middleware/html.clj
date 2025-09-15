(ns louhi.middleware.html
  (:require [dev.onionpancakes.chassis.core :as h]
            [louhi.html.util :as hu]))


;; Regular ring middleware, use when not using reitit:

(defn wrap-html-response
  ([handler wrap-html-page] (wrap-html-response handler wrap-html-page hu/html-headers))
  ([handler wrap-html-page html-headers]
   (let [partial-page  (fn [body]
                         (when body
                           (h/html body)))
         full-page     (fn [body]
                         (when body
                           (h/html (wrap-html-page body))))
         merge-headers (fn [headers]
                         (merge html-headers headers))]
     (fn [req]
       (when-let [resp (handler req)]
         (-> resp
             (update :body (if (hu/partial-page-request? req)
                             partial-page
                             full-page))
             (update :headers merge-headers)))))))
