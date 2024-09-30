(ns louhi.htmx
  (:require [dev.onionpancakes.chassis.core :as h]))


(set! *warn-on-reflection* true)


;;
;; Middleware that converts response from hiccup syntax (extended with chassis directives) to 
;; a HTML string. Compiled into all routes that do not have `:htmx false` option.
;;
;; For more information on chassis, see https://github.com/onionpancakes/chassis
;;


(def wrap-htmx
  {:name    :htmx/wrap-htmx
   :compile (fn [{:keys [htmx]} _]
              (when-not (false? htmx)
                (fn [handler]
                  (fn [req]
                    (when-let [resp (handler req)]
                      (-> resp
                          (update :headers assoc "content-type" "text/html; charset=utf-8")
                          (update :body h/html)))))))})
