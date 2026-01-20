(ns simple.content
  (:require [louhi.html.util :as html]
            [jsonista.core :as json]))


(def html-head
  [:head {:lang "en"}
   [:meta {:charset "utf-8"}]
   [:title "Simple Louhi web app"]
   [:meta {:name    "viewport"
           :content "width=device-width, initial-scale=1.0"}]
   [:meta {:name    "htmx-config"
           :content (json/write-value-as-string {:defaultSwapStyle "outerHTML"})}]
   (html/style "/styles.css")
   (html/script "/htmx.js")
   (html/script "/dev/watch")])


(defn link [opts & body]
  (into [:a (merge {:hx-target   "main"
                    :hx-push-url "true"
                    :hx-get      (:href opts)}
                   opts)]
        body))


(def nav-bar
  [:nav
   [:ul
    [:li (link {:class "title"
                :href  "/"}
               "Example")]
    [:li (link {:href "/hello"}
               "Hello")]
    [:li (link {:href "/about"}
               "About")]]])


(defn full-html-page [main]
  [html/doctype-html5
   [:html
    html-head
    [:body
     nav-bar
     main]]])
