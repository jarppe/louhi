(ns louhi.html.util
  (:require [dev.onionpancakes.chassis.core :as h]))


(def doctype-html5     h/doctype-html5)
(def content-type-html "text/html; charset=utf-8")


(def html-headers {"content-type" content-type-html
                   "vary"         "cookie, accept-language, hx-request, x-alpine-request"})


(defn script
  ([script-name] (script script-name nil))
  ([script-name {:keys [type defer async integrity]}]
   [:script {:src       script-name
             :type      type
             :defer     defer
             :async     async
             :integrity integrity}]))


(defn style
  ([style-name] (style style-name nil))
  ([style-name {:keys [integrity]}]
   [:link {:href      style-name
           :type      "text/css"
           :rel       "stylesheet"
           :integrity integrity}]))


(defn partial-page-request? [req]
  (let [headers (-> req :headers)]
    (or (contains? headers "hx-request")
        (contains? headers "x-alpine-request"))))