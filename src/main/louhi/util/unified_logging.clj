(ns louhi.util.unified-logging
  "Require this namespace to unify all logging libraries by bridging them into SLF4J")


#_{:clojure-lsp/ignore [:clojure-lsp/unused-public-var]}
(defonce _init-logging
  (do (System/setProperty "org.jboss.logging.provider" "slf4j")
      (org.slf4j.bridge.SLF4JBridgeHandler/removeHandlersForRootLogger)
      (org.slf4j.bridge.SLF4JBridgeHandler/install)))
