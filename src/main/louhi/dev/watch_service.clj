(ns louhi.dev.watch-service
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [integrant.core :as ig]
            [ring.core.protocols :as p]
            [louhi.http.status :as status])
  (:import (java.nio.file Path
                          FileSystems
                          WatchEvent
                          WatchEvent$Kind
                          StandardWatchEventKinds)
           (java.nio.charset StandardCharsets)
           (java.util.concurrent TimeUnit)))


(set! *warn-on-reflection* true)


(def ^:private ^java.nio.file.WatchEvent$Kind/1 watch-kinds
  (into-array WatchEvent$Kind [StandardWatchEventKinds/ENTRY_CREATE
                               StandardWatchEventKinds/ENTRY_MODIFY
                               StandardWatchEventKinds/ENTRY_DELETE]))


;;
;; Create new `java.nio.file.WatchService` to monitor file-system changes in
;; given directory. Calls `on-watch-event` when a change in file system is detected.
;; Returns a closeable to close the watcher instance and release it's resources.
;;


(defn- new-watcher [{:keys [root dir uri]} on-watch-event]
  (let [root   (->> (or root ".") (io/file) (.toPath))
        dir    (->> (or dir "") (io/file) (.toPath) (.resolve root))
        uri    (or uri "/")
        watch  (-> (FileSystems/getDefault) (.newWatchService))
        thread (.start (Thread/ofVirtual)
                       (fn []
                         (try
                           (while true
                             (when-let [k (.poll watch 10 TimeUnit/SECONDS)]
                               (try
                                 (doseq [^WatchEvent event (.pollEvents k)]
                                   (on-watch-event {:event :file
                                                    :data  (->> (.resolve dir ^Path (.context event))
                                                                (.relativize root)
                                                                (str uri))}))
                                 (finally
                                   (.reset k)))))
                           (catch java.nio.file.ClosedWatchServiceException _)
                           (catch InterruptedException _)
                           (catch Exception e
                             (.println System/err (format "error: unexpected error on dev file watcher: %s: %s"
                                                          (-> e (.getClass) (.getName))
                                                          (-> e (.getMessage))))
                             (.printStackTrace e System/err)
                             (throw e)))))]
    (.register dir watch watch-kinds)
    (reify java.io.Closeable
      (close [_]
        (try
          (.interrupt thread)
          (.close watch)
          (catch Throwable e
            (.println System/err (format "error: unexpected error while closing dev watcher: %s: %s"
                                         (-> e (.getClass) (.getName))
                                         (-> e (.getMessage))))
            (.printStackTrace e System/err)))))))


;;
;; Make a watch service to watch file-system and report on all changes.
;;
;; The `watch-locations` must be a seq of maps where each map contains:
;; * `:root` The root directory for the watch
;; * `:dir`  The directory relative to the root
;; * `:uri`  URI prefix to append to the reported file names
;;


(defrecord WatchService [fs-watchers listeners]
  java.io.Closeable
  (close [_]
    (doseq [watcher fs-watchers]
      (java.io.Closeable/.close watcher))))


(defn make-watch-service ^java.io.Closeable [watch-locations]
  (let [listeners      (atom #{})
        on-watch-event (fn [event]
                         (doseq [listener @listeners]
                           (listener event)))
        fs-watchers    (mapv (fn [watch-location]
                               (new-watcher watch-location on-watch-event))
                             watch-locations)]
    (->WatchService fs-watchers listeners)))


(defn close-watch-service [^WatchService watch-service]
  (when watch-service
    (.close watch-service)))


(defn- create-listener [watch-service]
  (let [events   (java.util.concurrent.LinkedBlockingQueue.)
        listener (fn
                   ([] (.take events))
                   ([event] (.add events event)))]
    (swap! (:listeners watch-service) conj listener)
    listener))


(defn- remove-listener [watch-service listener]
  (swap! (:listeners watch-service) disj listener)
  nil)


;;
;; ===================================================================
;; Middleware for dev watch service:
;; ===================================================================
;;


;;
;; Handler for reposinding to SSE request:
;;


(defn- sse-send [^java.io.Writer out event data]
  (doto out
    (.write "event: ")
    (.write (name event))
    (.write "\r\n")
    (.write "data: ")
    (.write (str (or data "")))
    (.write "\r\n")
    (.write "\r\n")
    (.flush)))


(defn- make-events-handler [watch-service]
  (fn [_req]
    {:status  200
     :headers {"content-type" "text/event-stream"}
     :body    (reify p/StreamableResponseBody
                (write-body-to-stream [_body _response output-stream]
                  (let [out    (io/writer output-stream)
                        listen (create-listener watch-service)]
                    (try
                      (sse-send out :connected "connected")
                      (doseq [{:keys [event data]} (repeatedly listen)]
                        (sse-send out event data))
                      (finally
                        (remove-listener watch-service listen))))))}))


;;
;; Handler for dev-watcher JavaScript file:
;;


(defn- make-dev-watcher-js-handler [uri]
  (let [body (let [baos (java.io.ByteArrayOutputStream.)]
               (with-open [in  (-> (io/resource "louhi/dev/dev-watcher.js")
                                   (io/input-stream))
                           out (java.util.zip.GZIPOutputStream. baos)]
                 (.write out (-> (.readAllBytes in)
                                 (String. StandardCharsets/UTF_8)
                                 (str/replace "$EVENT_URL$" uri)
                                 (.getBytes StandardCharsets/UTF_8)))
                 (.flush out))
               (.toByteArray baos))
        etag (->> body (hash) (abs) (format "\"%08x\""))
        resp {:status  200
              :headers {"content-type"     "application/javascript; charset=utf-8"
                        "content-encoding" "gzip"
                        "cache-control"    "public, no-cache"
                        "etag"             etag}
              :body    body}]
    (fn [req]
      (if (-> req :headers (get "if-none-match") (= etag))
        (-> resp
            (assoc :status status/not-modified)
            (dissoc :body))
        resp))))


(defn- make-dev-watch-handler [watch-service uri]
  (let [events-handler (make-events-handler watch-service)
        js-handler     (make-dev-watcher-js-handler uri)]
    (fn [req]
      (when (-> req :uri (= uri))
        (if (-> req :headers (get "accept") (= "text/event-stream"))
          (events-handler req)
          (js-handler req))))))


;;
;; WatchService handler as component:
;;


(defmethod ig/init-key ::watch-handler [_ {:keys [watch-locations uri]
                                           :or   {uri "/dev/watch"}}]
  (let [watch-service (make-watch-service watch-locations)
        watch-handler (make-dev-watch-handler watch-service uri)]
    {:watch-service watch-service
     :watch-handler watch-handler}))


(defmethod ig/halt-key! ::watch-handler [_ {:keys [watch-service]}]
  (when watch-service (close-watch-service watch-service)))


(defmethod ig/resolve-key ::watch-handler [_ {:keys [watch-handler]}]
  watch-handler)
