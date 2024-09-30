(ns louhi.server.jetty
  (:require [ring.adapter.jetty9 :as jetty]
            [louhi.server.impl :as server]))


#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn make-server [handler config]
  (let [server (jetty/run-jetty handler {:host                 (-> config :host)
                                         :port                 (-> config :port)
                                         :join?                false
                                         :virtual-threads?     true
                                         :allow-null-path-info true})]
    (server/->Server (fn [] (-> server (.getConnectors) (first) (.getLocalPort)))
                     (fn [] (jetty/stop-server server)))))


(comment
  (def server (make-server (constantly {:status 200
                                        :body   "Hey ho!"})
                           {:host "127.0.0.1"
                            :port 0}))
  (server/server-port server)
  (server/close-server server)
  ;
  )
