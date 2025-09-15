(ns simple.handlers
  (:require [louhi.middleware.cache-control :refer [wrap-cache-headers]]
            [louhi.middleware.etag :refer [wrap-etag]]
            [louhi.middleware.html :refer [wrap-html-response]]
            [louhi.middleware.security-headers :refer [wrap-security-headers]] 
            [simple.content :as content]))


(defn index [req]
  (when (and (-> req :request-method (= :get))
             (-> req :uri (= "/")))
    {:status 200
     :body   [:main#main
              [:h1 "Welcome to simple demo"]]}))


(defn hello [req]
  (when (and (-> req :request-method (= :get))
             (-> req :uri (= "/hello")))
    {:status 200
     :body   [:main#main
              [:h1 "Hello"]]}))


(defn about [req]
  (when (and (-> req :request-method (= :get))
             (-> req :uri (= "/about")))
    {:status 200
     :body   [:main#main
              [:h1 "About"]]}))


(defn make-handler []
  (-> (some-fn index
               hello
               about)
      (wrap-html-response content/full-html-page)
      #_(wrap-security-headers)
      (wrap-etag)
      (wrap-cache-headers)))
