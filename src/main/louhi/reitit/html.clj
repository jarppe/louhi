(ns louhi.reitit.html
  (:require [muuntaja.format.core]
            [dev.onionpancakes.chassis.core :as h]
            [louhi.http.security-headers :as su]
            [louhi.html.util :as hu]))


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


;;
;; HTML page middleware for HTMX and Alpine-ajax support:
;;


(def wrap-htmx
  {:name    :wrap-html-page-middleware
   :compile (fn [{:keys [htmx]} _]
              (when htmx
                (fn
                  ([_] (throw (ex-info "wrap-html-page middleware requires options" {})))
                  ([handler {:keys [to-full-page]}]
                   (when-not to-full-page
                     (throw (ex-info "wrap-html-page middleware requires option to-full-page" {})))
                   (let [partial-page-headers hu/html-headers
                         full-page-headers    (merge hu/html-headers su/security-headers)]
                     (fn [req]
                       (when-let [resp (handler req)]
                         (if (hu/partial-page-request? req)
                           (-> resp
                               (update :headers merge partial-page-headers))
                           (-> resp
                               (update :headers merge full-page-headers)
                               (update :body to-full-page))))))))))})
