(ns louhi.reitit.html
  "HTML format support for Reitit"
  (:require [muuntaja.format.core]
            [dev.onionpancakes.chassis.core :as h]))


(set! *warn-on-reflection* true)


;;
;; Malli HTML format support:
;;


(defn- html-encoder [_]
  (reify
    muuntaja.format.core/EncodeToBytes
    (encode-to-bytes [_ data charset]
      (.getBytes (h/html data) ^String charset))
    muuntaja.format.core/EncodeToOutputStream
    (encode-to-output-stream [_ data charset]
      (fn [^java.io.OutputStream output-stream]
        (let [appendable (java.io.OutputStreamWriter. output-stream ^String charset)]
          (h/write-html appendable data)
          (.flush appendable))))))


(defn- html-decoder [_]
  (reify
    muuntaja.format.core/Decode
    (decode [_ _data _charset]
      (throw (ex-info "can't decode html" {})))))


(def html-format
  (muuntaja.format.core/map->Format
   {:name    "text/html"
    :encoder [html-encoder]
    :decoder [html-decoder]}))
