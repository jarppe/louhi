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
                  ([handler {:keys [to-full-page security-headers-override]}]
                   (let [full-page-headers (merge hu/html-headers su/security-headers security-headers-override)]
                     (fn [req]
                       (when-let [resp (handler req)]
                         (if (or (nil? to-full-page)
                                 (hu/partial-page-request? req))
                           (-> resp
                               (update :headers merge hu/html-headers))
                           (-> resp
                               (update :headers merge full-page-headers)
                               (update :body to-full-page))))))))))})
