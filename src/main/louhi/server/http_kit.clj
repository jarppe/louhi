(ns louhi.server.http-kit
  (:require [org.httpkit.server :as http-kit]
            [louhi.server.impl :as server])
  (:import (java.util.concurrent Executors)))


#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn make-server [handler config]
  (let [http-kit (http-kit/run-server handler
                                      {:ip                   (-> config :host)
                                       :port                 (-> config :port)
                                       :legacy-return-value? false
                                       :worker-pool          (Executors/newVirtualThreadPerTaskExecutor)})]
    (server/->Server (fn [] (http-kit/server-port http-kit))
                     (fn [] (http-kit/server-stop! http-kit)))))


(comment
  (def server (make-server (constantly {:status 200
                                        :body   "Hey ho!"})
                           {:host "127.0.0.1"
                            :port 0}))
  (server/server-port server)
  (server/close-server server)
  ;
  )