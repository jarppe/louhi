(ns louhi.dev.watch-service
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [jsonista.core :as json]
            [ring.websocket :as ws])
  (:import (java.nio.file Path
                          FileSystems
                          WatchEvent
                          WatchEvent$Kind
                          StandardWatchEventKinds)
           (java.nio.charset StandardCharsets)
           (java.util.concurrent TimeUnit)
           (java.util.concurrent.atomic AtomicLong)))


;;
;; NOTE:
;;   This namespace has partial support for SSE, but it's not functional yet
;;


(set! *warn-on-reflection* true)


(def ^java.nio.file.WatchEvent$Kind/1 watch-kinds
  (into-array WatchEvent$Kind [StandardWatchEventKinds/ENTRY_CREATE
                               StandardWatchEventKinds/ENTRY_MODIFY
                               StandardWatchEventKinds/ENTRY_DELETE]))


;;
;; Create new `java.nio.file.WatchService` to monitor file-system changes
;; in given directory and returns a function to close the watcher instance.
;;


(def ^:private keep-alive-message
  {:type  :keep-alive
   :event "keep-alive"})


(defn- new-watcher [{:keys [root dir uri]} on-watch-event]
  (let [root   (->> root (io/file) (.toPath))
        dir    (->> (or dir "") (io/file) (.toPath) (.resolve root))
        uri    (or uri "/")
        watch  (-> (FileSystems/getDefault) (.newWatchService))
        thread (.start (Thread/ofVirtual)
                       (fn []
                         (try
                           (while true
                             (if-let [k (.poll watch 10 TimeUnit/SECONDS)]
                               (try
                                 (doseq [^WatchEvent event (.pollEvents k)]
                                   (on-watch-event {:type  :file
                                                    :event (-> (.kind event)
                                                               (.name)
                                                               (subs 6) ; strip leading "ENTRY_"
                                                               (str/lower-case))
                                                    :file  (->> (.resolve dir ^Path (.context event))
                                                                (.relativize root)
                                                                (str uri))}))
                                 (finally
                                   (.reset k)))
                               (on-watch-event keep-alive-message)))
                           (catch java.nio.file.ClosedWatchServiceException _)
                           (catch InterruptedException _)
                           (catch Exception e
                             (.println System/err (format "error: unexpected error on dev file watcher: %s: %s"
                                                          (-> e (.getClass) (.getName))
                                                          (-> e (.getMessage))))
                             (.printStackTrace e System/err)
                             (throw e)))))]
    (.register dir watch watch-kinds)
    (fn []
      (.interrupt thread)
      (.close watch))))


;;
;; Make a watch service to watch file-system and report on all changes.
;;
;; The `watch-locations` must be a seq of maps where each map contains:
;; * `:root` The root directory for the watch
;; * `:dir`  The directory relative to the root
;; * `:uri`  URI prefix to append to the reported file names
;;


(defn make-watch-service [watch-locations]
  (let [listeners      (atom {})
        next-key       (AtomicLong.)
        on-watch-event (fn [event]
                         (doseq [listener (vals @listeners)]
                           (try
                             (listener event)
                             (catch Exception e
                               (.println System/err (format "error: unexpected error when delivering event: %s: %s"
                                                            (-> e (.getClass) (.getName))
                                                            (-> e (.getMessage))))
                               (.printStackTrace e System/err)))))
        watchers       (mapv (fn [watch-location]
                               (new-watcher watch-location on-watch-event))
                             watch-locations)]
    {:listeners listeners
     :next-key  next-key
     :watchers  watchers}))


(defn close-watch-service [watch-service]
  (let [{:keys [watchers listeners]} watch-service]
    (doseq [watcher watchers]
      (watcher))
    (doseq [listener (-> listeners (deref) (vals))]
      (listener))))


;;
;; Private API for watch-service:
;;


(defn- next-listener-key [watch-service]
  (let [^AtomicLong next-key (:next-key watch-service)]
    (str (.getAndIncrement next-key))))


(defn- add-listener [watch-service listener-key on-event]
  (swap! (:listeners watch-service) assoc listener-key on-event)
  nil)


(defn- remove-listener [watch-service listener-key]
  (swap! (:listeners watch-service) dissoc listener-key)
  nil)


;;
;; ===================================================================
;; Middleware for dev watch service:
;; ===================================================================
;;


(defn- make-get-js-handler [type uri]
  (let [replacements {"EVENT_TYPE" (name type)
                      "EVENT_URL"  uri}
        body         (let [baos (java.io.ByteArrayOutputStream.)]
                       (with-open [in  (-> (io/resource "louhi/dev/dev-watcher.js")
                                           (io/input-stream))
                                   out (java.util.zip.GZIPOutputStream. baos)]
                         (.write out (-> (.readAllBytes in)
                                         (String. StandardCharsets/UTF_8)
                                         (str/replace #"\$([^$]+)\$" (comp replacements second))
                                         (.getBytes StandardCharsets/UTF_8)))
                         (.flush out))
                       (.toByteArray baos))
        etag         (->> body (hash) (abs) (format "\"%08x\""))
        resp         {:status  200
                      :headers {"content-type"     "application/javascript; charset=utf-8"
                                "content-encoding" "gzip"
                                "cache-control"    "public, no-cache"
                                "etag"             etag}
                      :body    body}]
    (fn [req]
      (if (-> req :headers (get "if-none-match") (= etag))
        (-> resp (assoc :status 304) (dissoc :body))
        resp))))


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


;; (require [ring.sse :as :sse])
;; (defn- make-sse-listener [sse]
;;   (fn
;;     ([]
;;      (try
;;        (sse/close sse)
;;        (catch Exception _)))
;;     ([events]
;;      (try
;;        (sse/send sse {:data (json/write-value-as-string events)})
;;        (catch Exception _
;;          (try
;;            (sse/close sse)
;;            (catch Exception _)))))))


(defn events-resp [watch-service]
  (let [listener-key (next-listener-key watch-service)]
    {:ring.websocket/listener {:on-open  (fn [socket] (add-listener watch-service listener-key (make-socket-listener socket)))
                               :on-close (fn [_ _ _]  (remove-listener watch-service listener-key))
                               :on-error (fn [_ _]    (remove-listener watch-service listener-key))}
     ;; :ring.sse/listener       {:on-open  (fn [sse]    (add-listener watch-service listener-key (make-sse-listener sse)))}
     }))


(defn- make-dev-watch-handler [watch-service type uri]
  (let [get-js-handler (make-get-js-handler type uri)]
    (fn [req]
      (when (-> req :uri (= uri))
        ;; or sse/sse-request?
        (if (ws/upgrade-request? req)
          (events-resp watch-service)
          (get-js-handler req))))))


(defn wrap-dev-watcher
  ([handler watch-service] (wrap-dev-watcher handler watch-service nil))
  ([handler watch-service {:keys [type uri]
                           :or   {uri  "/dev/watch"
                                  type :ws}}]
   (if watch-service
     (some-fn (make-dev-watch-handler watch-service type uri)
              handler)
     handler)))
