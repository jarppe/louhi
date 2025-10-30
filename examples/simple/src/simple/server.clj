(ns simple.server
  (:require [louhi.server.hirundo :as server]
            [louhi.handler.resources :as resources]
            [louhi.handler.not-found :as not-found]
            [louhi.middleware.errors :refer [wrap-handle-exceptions wrap-handle-errors]]
            [louhi.middleware.request-logger :refer [wrap-request-logger]]
            [louhi.middleware.clacks-overhead :refer [wrap-clacks-overhead]]
            [louhi.dev.watch-service :as watch-service :refer [wrap-dev-watcher]]
            [simple.handlers :as handlers]))


(defn start-server []
  (let [watch-service (watch-service/make-watch-service [{:root "examples/simple/public"}])
        handler       (-> (some-fn (handlers/make-handler)
                                   (resources/resources-handler (resources/directory-resources-repository "./examples/simple/public" "/static/"))
                                   (not-found/not-found-handler))
                          (wrap-handle-exceptions)
                          (wrap-clacks-overhead)
                          (wrap-request-logger {:stacktrace true})
                          (wrap-handle-errors)
                          (wrap-dev-watcher watch-service))
        config        {:host (or (System/getenv "HOST") "127.0.0.1")
                       :port (or (System/getenv "PORT") "8080")}]
    (server/make-server handler (assoc config :on-close (fn [] (.close watch-service))))))


(comment
  (def server (start-server))
  (.close server)

  (def repo (resources/directory-resources-repository "./examples/simple/public" "/static/"))
  (clojure.java.io/file "./examples/simple/public" "/style")
  (repo "/static/style.css")
  )