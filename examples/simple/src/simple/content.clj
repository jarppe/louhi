(ns simple.content
  (:require [louhi.html.util :as html]))


(def html-head
  [:head {:lang "en"}
   [:meta {:charset "utf-8"}]
   [:title "Simple Louhi web app"]
   [:meta {:name    "viewport"
           :content "width=device-width, initial-scale=1.0"}]
   (html/style "/static/styles.css")
   (html/script "https://cdn.jsdelivr.net/npm/@imacrayon/alpine-ajax@0.12.4/dist/cdn.min.js" {:defer true})
   (html/script "https://cdn.jsdelivr.net/npm/alpinejs@3.14.1/dist/cdn.min.js" {:defer true})])


(def nav-bar
  [:nav
   [:ul
    [:li [:a.title {:href     "/"
                    :x-target "main"}
          "Example"]]
    [:li [:a {:href     "/hello"
              :x-target "main"}
          "Hello"]]
    [:li [:a {:href     "/about"
              :x-target "main"}
          "About"]]]])


(defn full-html-page [main]
  [html/doctype-html5
   [:html
    html-head
    [:body
     nav-bar
     main]]])
