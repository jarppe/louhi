(ns louhi.server.jetty
  "Jetty Web server adapter"
  (:require [ring.adapter.jetty9 :as jetty]
            [louhi.server.core :as core]
            [integrant.core :as ig])
  (:import (org.eclipse.jetty.server Server
                                     ServerConnector)))


(set! *warn-on-reflection* true)


;;
;; Jetty impl:
;;


(def jetty-config-defaults {:join?                false
                            :virtual-threads?     true
                            :allow-null-path-info true
                            :host                 "127.0.0.1"
                            :port                 0})


(defn create-server
  "Create and start an instance of Jetty Web server. Accepts a ring handler and options.

     Currently supported options are:
        :host      Host name to bind the server socket, defaults to \"127.0.0.1\".
        :port      Port to use, or 0 to let server pick any available port number. Defaults to 0.

     The returned server object implements `java.io.Closeable`, so you can use it with
     clojure.core/with-open:

     ```
     (with-open [s (create-server ...)]
       ... do something with server
     )
     ```"
  ^louhi.server.core.Server
  [handler config]
  (let [^Server server (jetty/run-jetty handler (-> (merge jetty-config-defaults config)
                                                    (update :port (fn [port] (if (string? port) (parse-long port) port)))))]
    (core/server {:server server
                  :impl   ::jetty
                  :close  (fn [] (jetty/stop-server server))
                  :port   (-> server (.getConnectors) (aget 0) (ServerConnector/.getLocalPort))})))


;;
;; Integrant:
;;


(defmethod ig/init-key ::server [_ {:keys [handler config]}]
  (create-server handler config))


(defmethod ig/halt-key! ::server [_ ^louhi.server.core.Server server]
  (when server (.close server)))


(comment
  (with-open [server (create-server (constantly {:status 200
                                                 :body   "Hey ho!"})
                                    nil)]
    (pr-str server))
  ;
  )