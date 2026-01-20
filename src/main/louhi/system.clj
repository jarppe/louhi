(ns louhi.system
  (:require [integrant.core :as ig]
            [louhi.server.jetty]
            [louhi.reitit.handler]))


(def system nil)


(defn stop-system []
  (alter-var-root #'system (fn [old-system]
                             (when old-system (ig/halt! old-system))
                             nil)))


(defn start-system [system-map]
  (alter-var-root #'system (fn [old-system]
                             (when old-system (ig/halt! old-system))
                             (ig/init system-map))))
