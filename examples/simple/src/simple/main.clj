(ns simple.main
  (:require [louhi.util.unified-logging]
            [louhi.system]
            [simple.system :as system])
  (:gen-class))


(defn -main [& _args]
  (println "main: starting...")
  (louhi.system/start-system (system/system-map))
  (println "main: server ready")
  (deref (promise)))
