(ns user
  (:require [integrant.repl :as igr]
            [integrant.repl.state :as state]))


(igr/set-prep! (fn [] ((requiring-resolve 'simple.system/prepare-system))))


#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn start []
  (igr/init))


#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn stop []
  (igr/halt))


#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn reset []
  (igr/reset))


#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn system []
  state/system)

