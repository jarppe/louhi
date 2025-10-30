(ns louhi.dev.watch-service
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [ring.core.protocols :as p])
  (:import (java.nio.file Path
                          FileSystems
                          WatchEvent
                          WatchEvent$Kind
                          StandardWatchEventKinds)
           (java.nio.charset StandardCharsets)
           (java.util.concurrent TimeUnit)))


(set! *warn-on-reflection* true)


(def ^java.nio.file.WatchEvent$Kind/1 watch-kinds
  (into-array WatchEvent$Kind [StandardWatchEventKinds/ENTRY_CREATE
                               StandardWatchEventKinds/ENTRY_MODIFY
                               StandardWatchEventKinds/ENTRY_DELETE]))


;;
;; Create new `java.nio.file.WatchService` to monitor file-system changes in
;; given directory. Calls `on-watch-event` when a change in fs is detected.
;; Returns a closeable to close the watcher instance and release it's resources.
;;


(defn- new-watcher [{:keys [root dir uri]} on-watch-event]
  (let [root   (->> root (io/file) (.toPath))
        dir    (->> (or dir "") (io/file) (.toPath) (.resolve root))
        uri    (or uri "/")
        watch  (-> (FileSystems/getDefault) (.newWatchService))
        thread (.start (Thread/ofVirtual)
                       (fn []
                         (try
                           (while true
                             (println "poll events:" (pr-str dir))
                             (when-let [k (.poll watch 10 TimeUnit/SECONDS)]
                               (println "got events")
                               (try
                                 (doseq [^WatchEvent event (.pollEvents k)]
                                   (on-watch-event {:event :file
                                                    :data  (->> (.resolve dir ^Path (.context event))
                                                                (.relativize root)
                                                                (str uri))}))
                                 (finally
                                   (.reset k)))))
                           (catch java.nio.file.ClosedWatchServiceException _
                             (println "watch closed"))
                           (catch InterruptedException _
                             (println "watch interrupted"))
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
            (println "uh no" e)))))))


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
                         (println "watch-service:" (pr-str event))
                         (doseq [listener @listeners]
                           (println "watch-service: send...")
                           (listener event))
                         (println "watch-service: done"))
        fs-watchers    (mapv (fn [watch-location]
                               (new-watcher watch-location on-watch-event))
                             watch-locations)]
    (->WatchService fs-watchers listeners)))


(defn close-watch-service [^java.io.Closeable watch-service]
  (when watch-service
    (.close watch-service)))


(defn- get-listener [watch-service]
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


(defn- make-events-handler [watch-service]
  (fn [_req]
    (println "events-handler:"
             "\ncontent-type:" (-> _req :headers (get "accept") (pr-str)))
    {:status  200
     :headers {"content-type" "text/event-stream"}
     :body    (reify p/StreamableResponseBody
                (write-body-to-stream [_body _response output-stream]
                  (let [out    (io/writer output-stream)
                        listen (get-listener watch-service)]
                    (try
                      (doseq [{:keys [event data]} (repeatedly listen)]
                        (doto out
                          (.write "event: ")
                          (.write (name event))
                          (.write "\r\n")
                          (.write "data: ")
                          (.write (str (or data "")))
                          (.write "\r\n")
                          (.write "\r\n")
                          (.flush)))
                      (finally
                        (remove-listener watch-service listen))))))}))


;;
;; Handler for dev-watcher JavaScript file:
;;


(defn- make-dev-watcher-js-handler [uri]
  (let [replacements {"EVENT_URL" uri}
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
      (println "dev-watcher-js-handler:"
               "\ncontent-type:" (-> req :headers (get "accept") (pr-str))
               "\nif-none-match:" (-> req :headers (get "if-none-match") (pr-str)) "=>" (-> req :headers (get "if-none-match") (= etag)))
      (if (-> req :headers (get "if-none-match") (= etag))
        (-> resp
            (assoc :status 304)
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


(defn wrap-dev-watcher
  ([handler watch-service] (wrap-dev-watcher handler watch-service nil))
  ([handler watch-service {:keys [uri]
                           :or   {uri "/dev/watch"}}]
   (if watch-service
     (some-fn (make-dev-watch-handler watch-service uri)
              handler)
     handler)))
