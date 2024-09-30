(ns init
  (:require [babashka.process :as p]
            [babashka.fs :as fs]
            [vend-deps :as v]))


#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn init []
  (println "docker: building images")
  (fs/copy "./deps.edn" "./examples/docker/dev" {:replace-existing true})
  (p/shell {:dir "./examples/docker/dev"} "docker build --tag louhi/dev:latest .")

  (println "vendor: downloading vendor scripts...")
  (v/download-all)

  (println "\nDev environment initialization SUCCESS"))
