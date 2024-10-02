(ns simple.app.core
  (:require [ring.util.http-response :as resp]
            [simple.app.html :as html]))


(defn app-routes [env]
  [""
   ["/" {:get {:name    :app/index
               :handler (html/main-page-handler env)}}]
   ["/foo" {:get {:name    :app/foo
                  :handler (fn [req]
                             (-> [(html/page-header req)
                                  [:main
                                   [:article
                                    [:h1 "Foo"]]]]
                                 (resp/ok)))}}]
   ["/bar" {:get {:name    :app/bar
                  :handler (fn [req]
                             (-> [(html/page-header req)
                                  [:main
                                   [:article
                                    [:h1 "Bar"]]]]
                                 (resp/ok)))}}]])
