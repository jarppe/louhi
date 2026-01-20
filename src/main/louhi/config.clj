(ns louhi.config
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [aero.core :as aero]))


(defmethod aero/reader 'file [_ _ value]
  (let [f (io/file value)]
    (when-not (.canRead f) (throw (ex-info (str "can't read file: " value) {:file value})))
    (str/trim (slurp f))))


(defn config
  ([]            (config "config.edn" nil))
  ([config-file] (config config-file nil))
  ([config-file opts]
   (aero/read-config config-file opts)))
