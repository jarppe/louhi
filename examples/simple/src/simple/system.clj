(ns simple.system
  (:require [integrant.core :as ig]
            [simple.config :as config]
            [simple.resources]
            [simple.app.user-store]))


(set! *warn-on-reflection* true)


#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defonce _init
  (do (org.slf4j.bridge.SLF4JBridgeHandler/removeHandlersForRootLogger)
      (org.slf4j.bridge.SLF4JBridgeHandler/install)
      (doto (java.util.concurrent.Executors/newVirtualThreadPerTaskExecutor)
        (set-agent-send-executor!)
        (set-agent-send-off-executor!))))


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
