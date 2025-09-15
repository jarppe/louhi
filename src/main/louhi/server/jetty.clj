(ns louhi.server.jetty
  (:require [ring.adapter.jetty9 :as jetty]
            [louhi.server.core :as core])
  (:import (org.eclipse.jetty.server Server
                                     ServerConnector)))


(set! *warn-on-reflection* true)


(defn make-server ^louhi.server.core.Server [handler config]
  (let [^Server server (jetty/run-jetty handler (-> (merge {:join?                false
                                                            :virtual-threads?     true
                                                            :allow-null-path-info true
                                                            :host                 "127.0.0.1"
                                                            :port                 0}
                                                           config)
                                                    (update :port (fn [port] (if (string? port) (parse-long port) port)))))
        on-close (:on-close config)]
    (core/server {:impl  ::jetty
                  :close (fn []
                           (when on-close (on-close))
                           (jetty/stop-server server))
                  :port  (fn [] 
                           (-> server (.getConnectors) (aget 0) (ServerConnector/.getLocalPort)))})))


(comment
  (with-open [server (make-server (constantly {:status 200
                                               :body   "Hey ho!"})
                                  {:host "127.0.0.1"
                                   :port 0})]
    (pr-str server))
  ;
  )