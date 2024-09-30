(ns louhi.server
  (:require [malli.core :as malli]
            [louhi.server.impl :as impl]
            [louhi.dev.watch-service :as watch-service]
            [louhi.dev.watcher :as watcher]))


(set! *warn-on-reflection* true)


(def ServerConfig [:map
                   [:type [:or string? [:enum :http-kit :jetty :hirundo]]]
                   [:host {:optional true} string?]
                   [:port {:optional true} int?]
                   [:dev-watcher {:optional true}
                    [:map
                     [:enable boolean?]
                     [:root {:optional true} string?]
                     [:dirs {:optional true} [:vector string?]]]]])


#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn make-server [handler config]
  (let [config        (malli/coerce ServerConfig (merge {:host "127.0.0.1"
                                                         :port 8080}
                                                        config))
        watch-service (when (-> config :dev-watcher :enable)
                        (watch-service/make-watch-service (-> config :dev-watcher)))
        handler       (if watch-service
                        (watcher/wrap-dev-watcher handler watch-service)
                        handler)
        make-server   (-> (if (-> config :type string?)
                            (-> config :type)
                            (str "louhi.server." (-> config :type (name)) "/make-server"))
                          (symbol)
                          (requiring-resolve)
                          (deref))
        server        (make-server handler config)
        port          (impl/server-port server)]
    (vary-meta
     (fn []
       (watch-service/close-watch-service watch-service)
       (impl/close-server server))
     assoc ::server-port port)))


#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn stop-server [server]
  (when server
    (server)))


(comment
  (def server (make-server (constantly {:status 200
                                        :body   "Whoop"})
                           {:type :jetty
                            :port 18080}))
  (-> server (meta) ::server-port)
  ;; => 18080
  (server)
  ;
  )