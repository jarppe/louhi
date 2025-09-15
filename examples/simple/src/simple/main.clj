(ns simple.main
  (:require [louhi.util.unified-logging]
            [simple.server :as server])
  (:gen-class))


(defn -main [& _args]
  (println "main: starting...")
  (server/start-server)
  (println "main: server ready")
  (deref (promise)))
