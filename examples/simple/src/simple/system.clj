(ns simple.system
  (:require [clojure.java.io :as io]
            [integrant.core :as ig]
            [simple.config :as config]
            [simple.resources]
            [simple.app.user-store])
  (:import (java.util.concurrent Executors)))


(set! *warn-on-reflection* true)


(defonce _init-loom
  (doto (Executors/newVirtualThreadPerTaskExecutor)
    (set-agent-send-executor!)
    (set-agent-send-off-executor!)))


(defonce _init-jul
  (with-open [in (-> (io/resource "logging.properties")
                     (io/input-stream))]
    (.readConfiguration (java.util.logging.LogManager/getLogManager) in)))


(defmethod ig/init-key :simple/env [_ env]
  env)


(defn system-map [config]
  {:simple/resources-repo {:dev? (-> config :mode (= :dev))}
   :simple/user-store     {}

   :simple/env            {:config         config
                           :resources-repo (ig/ref :simple/resources-repo)
                           :user-store     (ig/ref :simple/user-store)}

   :simple/server         {:env (ig/ref :simple/env)}})


(defn prepare-system []
  (-> (config/load-config)
      (system-map)))


#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn start-system []
  (-> (prepare-system)
      (ig/init)))
