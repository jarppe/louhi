(ns louhi.server.hirundo
  (:require [s-exp.hirundo :as hirundo]
            [louhi.server.impl :as server]))


#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn make-server [handler config]
  (let [server (hirundo/start! handler
                               {:host (-> config :host)
                                :port (-> config :port)})]
    (server/->Server (fn [] (-> config :port)) ;; FIXME:
                     (fn [] (hirundo/stop! server)))))



(comment
  (def server (make-server (constantly {:status 200
                                        :body   "Hey ho!"})
                           {:host "127.0.0.1"
                            :port 8888}))
  (server/server-port server)
  (server/close-server server)
  ;
  )