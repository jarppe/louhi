(ns louhi.dev.watcher
  (:require [clojure.java.io :as io]
            [ring.websocket :as ws]
            [ring.util.http-response :as resp]
            [jsonista.core :as json]
            [louhi.dev.watch-service :as watch-service]))


(set! *warn-on-reflection* true)


;;
;; Wrap handler with dev watch service:
;;


(defn- make-socket-listener [socket]
  (fn
    ([]
     (try
       (ws/close socket)
       (catch Exception _)))
    ([events]
     (try
       (ws/send socket (json/write-value-as-string events))
       (catch Exception _
         (try
           (ws/close socket)
           (catch Exception _)))))))


(defn- make-ws-handler [watch-service]
  (fn [_req]
    (let [listener-key (watch-service/next-listener-key watch-service)]
      {::ws/listener {:on-open  (fn [socket]
                                  (watch-service/add-listener watch-service listener-key (make-socket-listener socket)))
                      :on-close (fn [_socket _code _reason]
                                  (watch-service/remove-listener watch-service listener-key))
                      :on-error (fn [_socket _ex]
                                  (watch-service/remove-listener watch-service listener-key))}})))


(defn- make-get-handler []
  (let [body              (-> (io/resource "louhi/dev/dev-watcher.js")
                              (io/input-stream)
                              (.readAllBytes))
        checksum          (-> (doto (java.util.zip.Adler32.)
                                (.update body 0 (alength body)))
                              (.getValue))
        etag              (str \" checksum \")
        headers           {"content-type"  "application/javascript; charset=utf-8"
                           "cache-control" "public, no-cache"
                           "etag"          etag}
        ok-resp           (-> (resp/ok body)
                              (assoc :headers headers))
        not-modified-resp (-> (resp/not-modified)
                              (assoc :headers headers))]
    (fn [req]
      (if (-> req :headers (get "if-none-match") (= etag))
        ok-resp
        not-modified-resp))))


(defn- make-dev-watch-handler [watch-service]
  (let [handle-ws  (make-ws-handler watch-service)
        handle-get (make-get-handler)]
    (fn [req]
      (cond
        (-> req (ws/upgrade-request?)) (handle-ws req)
        (-> req :request-method (= :get)) (handle-get req)
        :else (resp/bad-request)))))


(defn wrap-dev-watcher [handler watch-service]
  (let [dev-watch-handler (make-dev-watch-handler watch-service)]
    (fn [req]
      (if (-> req :uri (= "/dev/watch"))
        (dev-watch-handler req)
        (handler req)))))
