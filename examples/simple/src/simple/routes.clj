(ns simple.routes
  (:require [ring.util.http-response :as resp]))


(defn index [_req]
  (resp/ok [:main#main
            [:h1 "Welcome to simple demo"]]))


(defn hello [_req]
  (resp/ok [:main#hello
            [:h1 "Hello"]]))


(defn about [_req]
  (resp/ok [:main#about
            [:h1 "About"]]))


(defn routes [_env]
  ["" {:htmx true}
   ["/"      {:get {:handler index}}]
   ["/hello" {:get {:handler hello}}]
   ["/about" {:get {:handler about}}]])