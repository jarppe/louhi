(ns louhi.server.hirundo
  (:require [s-exp.hirundo :as hirundo]
            [louhi.server.core :as core])
  (:import (io.helidon.webserver WebServer)))


(set! *warn-on-reflection* true)


(defn make-server [handler config]
  (let [server (hirundo/start! handler (-> (merge {:host "127.0.0.1"
                                                     :port 0}
                                                    config)
                                             (update :port (fn [port] (if (string? port) (parse-long port) port)))))]
    (core/server {:impl   ::hirundo
                  :close  (fn []
                            (when-let [on-close (:on-close config)]
                              (on-close))
                            (hirundo/stop! server))
                  :port   (fn [] (-> server (WebServer/.port)))
                  :status (fn [] (-> server (WebServer/.isRunning) (if "running" "stopped")))})))


(defn close-server [^louhi.server.core.Server server]
  (when server
    (.close server)))


(comment
  (with-open [server (make-server (constantly {:status 200
                                               :body   "Hey ho!"})
                                  nil)]
    (pr-str server))
  ;
  (-> (merge {:host "127.0.0.1"
              :port 0}
             {:port 8080})
      (update :port Long/valueOf))
  )