(ns louhi.middleware.htmx
  "HTMX support"
  (:require [louhi.html.util :as hu]
            [louhi.http.security-headers :as su]))


;;
;; HTML page middleware for HTMX support:
;;


(def wrap-htmx
  {:name    :wrap-htmx
   :compile (fn [{:keys [htmx]} _]
              (when htmx
                (fn
                  ([_] (throw (ex-info "wrap-htmx middleware requires options" {})))
                  ([handler {:keys [to-full-page]}]
                   (when-not to-full-page
                     (throw (ex-info "wrap-html-page middleware requires option to-full-page" {})))
                   (let [partial-page-headers hu/html-headers
                         full-page-headers    (merge hu/html-headers su/security-headers)]
                     (fn [req]
                       (when-let [resp (handler req)]
                         (if (hu/partial-page-request? req)
                           (-> resp
                               (update :headers merge partial-page-headers))
                           (-> resp
                               (update :headers merge full-page-headers)
                               (update :body to-full-page))))))))))})
