(ns simple.main
  (:require [simple.system :as system])
  (:gen-class))


(defn -main [& _args]
  (println "main: starting...")
  (system/start-system))
